package com.job.bootstrap.service.impl;

import com.job.bootstrap.agent.memory.AgentMemoryCandidate;
import com.job.bootstrap.agent.memory.AgentMemoryLlmExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryRuleExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryWritePolicy;
import com.job.bootstrap.service.AgentMemoryCaptureService;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.vo.agent.AgentMemoryVO;
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

    private final AgentMemoryRuleExtractor ruleExtractor;
    private final AgentMemoryLlmExtractor llmExtractor;
    private final AgentMemoryWritePolicy writePolicy;
    private final AgentMemoryService agentMemoryService;
    private final AgentMemoryContextService agentMemoryContextService;

    /**
     * 从用户消息中捕获长期记忆。
     *
     * 方法步骤:
     * 1. 先用规则抽取高确定性事实，确保“助手称呼、城市、岗位、薪资”等能即时记住。
     * 2. 再用可选 LLM 抽取补充复杂表达；如果没有配置模型场景，则静默降级为空。
     * 3. 对同一 memoryType + memoryKey 去重，规则抽取优先级高于 LLM 抽取。
     * 4. 每条候选记忆都通过写入策略校验后才保存。
     * 5. 只要本轮写入了记忆，就重建用户画像摘要，避免后续 Prompt 注入全量记忆。
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
