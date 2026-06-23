package com.job.bootstrap.service.impl;

import com.job.bootstrap.agent.memory.AgentMemoryActionClassifier;
import com.job.bootstrap.agent.memory.AgentMemoryActionDecision;
import com.job.bootstrap.agent.memory.AgentMemoryCandidate;
import com.job.bootstrap.agent.memory.AgentMemoryLlmExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryRuleExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryWritePolicy;
import com.job.bootstrap.service.AgentMemoryCaptureService;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryActionType;
import com.job.enums.AgentMemorySourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆捕获服务实现
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 第一版长期记忆只从工具结果沉淀，普通聊天里的“以后叫你xxx”不会入库。
 * 2. 第二版在用户消息入库后立即捕获自然语言事实，让称呼、偏好、目标能跨会话生效。
 * 3. 捕获结果仍然要走写入策略，避免把低价值、敏感或疑问句写成长期事实。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryCaptureServiceImpl implements AgentMemoryCaptureService {

    private final AgentMemoryActionClassifier actionClassifier;
    private final AgentMemoryRuleExtractor ruleExtractor;
    private final AgentMemoryLlmExtractor llmExtractor;
    private final AgentMemoryWritePolicy writePolicy;
    private final AgentMemoryService agentMemoryService;
    private final AgentMemoryContextService agentMemoryContextService;

    /**
     * 从用户消息中捕获长期记忆。
     *
     * 方法步骤:
     * 1. 先判断用户输入对长期记忆库的动作: 设置、修改、删除、询问或普通聊天。
     * 2. DELETE_MEMORY 只归档明确识别到的记忆 key，不做抽取和写入。
     * 3. ASK_MEMORY 和 NORMAL_CHAT 直接跳过写入，避免“你记得我叫什么吗”被误存。
     * 4. SET_MEMORY 和 UPDATE_MEMORY 才进入规则抽取与可选 LLM 抽取。
     * 5. 每条候选记忆仍然要通过写入策略校验后才保存。
     * 6. 只要本轮写入或归档了记忆，就重建用户画像摘要。
     *
     * @param userId 当前用户 ID
     * @param conversationId 当前会话 ID
     * @param traceId 当前链路 ID
     * @param message 已脱敏用户输入
     * @return 本轮保存的记忆
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AgentMemoryVO> captureFromUserMessage(Long userId, Long conversationId, String traceId, String message) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(message)) {
            return List.of();
        }

        AgentMemoryActionDecision actionDecision = actionClassifier.classify(message);
        AgentMemoryActionType actionType = actionDecision.getActionType();
        if (AgentMemoryActionType.DELETE_MEMORY.equals(actionType)) {
            return handleDeleteMemory(userId, actionDecision);
        }
        if (!AgentMemoryActionType.SET_MEMORY.equals(actionType)
                && !AgentMemoryActionType.UPDATE_MEMORY.equals(actionType)) {
            log.debug(
                    "跳过长期记忆捕获，userId={}, traceId={}, action={}, reason={}",
                    userId,
                    traceId,
                    actionType,
                    actionDecision.getReason()
            );
            return List.of();
        }

        List<AgentMemoryCandidate> candidates = mergeCandidates(
                ruleExtractor.extract(message),
                llmExtractor.extract(userId, traceId, message)
        );
        if (CollectionUtils.isEmpty(candidates)) {
            return List.of();
        }

        List<AgentMemoryVO> saved = new ArrayList<>();
        for (AgentMemoryCandidate candidate : candidates) {
            if (!writePolicy.allowWrite(candidate)) {
                continue;
            }

            AgentLongTermMemory memory = agentMemoryService.saveOrUpdateMemory(
                    userId,
                    candidate.getMemoryType(),
                    candidate.getMemoryKey(),
                    candidate.getMemoryValue(),
                    candidate.getSummary(),
                    AgentMemorySourceType.USER_MESSAGE.name(),
                    conversationId,
                    candidate.getConfidence(),
                    candidate.getImportance()
            );
            saved.add(AgentMemoryVO.from(memory));
        }

        if (!saved.isEmpty()) {
            try {
                agentMemoryContextService.rebuildProfile(userId);
            } catch (Exception exception) {
                /*
                 * 画像重建失败不回滚已捕获的长期记忆。
                 * 原因: 原始事实比派生摘要更重要，摘要可以后续由后台手动重建。
                 */
                log.warn("长期记忆画像重建失败，userId={}, error={}", userId, exception.getMessage(), exception);
            }
        }
        return saved;
    }

    private List<AgentMemoryVO> handleDeleteMemory(Long userId, AgentMemoryActionDecision actionDecision) {
        if (CollectionUtils.isEmpty(actionDecision.getTargetMemoryKeys())) {
            /*
             * 删除类表达如果没有识别出目标 key，就不做任何归档。
             * 这样可以避免“不要记住这句话”误删用户已有的重要求职偏好。
             */
            log.debug(
                    "跳过长期记忆删除，userId={}, reason=未识别目标key, decisionReason={}",
                    userId,
                    actionDecision.getReason()
            );
            return List.of();
        }

        int archivedCount = agentMemoryService.archiveActiveMemoriesByKeys(
                userId,
                actionDecision.getTargetMemoryKeys()
        );
        if (archivedCount > 0) {
            try {
                agentMemoryContextService.rebuildProfile(userId);
            } catch (Exception exception) {
                log.warn("长期记忆删除后画像重建失败，userId={}, error={}", userId, exception.getMessage(), exception);
            }
        }
        return List.of();
    }

    private List<AgentMemoryCandidate> mergeCandidates(
            List<AgentMemoryCandidate> ruleCandidates,
            List<AgentMemoryCandidate> llmCandidates
    ) {
        Map<String, AgentMemoryCandidate> merged = new LinkedHashMap<>();
        putCandidates(merged, ruleCandidates);
        putCandidates(merged, llmCandidates);
        return new ArrayList<>(merged.values());
    }

    private void putCandidates(Map<String, AgentMemoryCandidate> merged, List<AgentMemoryCandidate> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return;
        }
        for (AgentMemoryCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.getMemoryType() == null
                    || !StringUtils.hasText(candidate.getMemoryKey())) {
                continue;
            }

            /*
             * putIfAbsent 保证规则抽取优先。
             * 规则候选先进入 Map，LLM 抽到相同 key 时不会覆盖规则结果。
             */
            merged.putIfAbsent(
                    candidate.getMemoryType().name() + ":" + candidate.getMemoryKey().trim(),
                    candidate
            );
        }
    }
}
