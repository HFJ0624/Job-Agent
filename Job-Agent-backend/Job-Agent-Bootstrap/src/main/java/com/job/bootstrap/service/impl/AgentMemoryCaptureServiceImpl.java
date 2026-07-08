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
 * Agent 长期记忆捕获服务实现，负责从用户自然语言消息中抽取长期事实并写入记忆库。
 *
 * <p>核心职责：
 * 在用户消息入库后立即识别其中隐含的记忆动作（SET/UPDATE/DELETE/ASK/NORMAL_CHAT），
 * 通过规则抽取 + LLM 抽取合并候选记忆，再经写入策略校验后落库，最后触发用户画像重建。
 * 让称呼、偏好、求职目标等关键事实能够跨会话生效。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Memory 子模块（用户消息记忆捕获层）。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.chat -> AgentMemoryCaptureService.captureFromUserMessage
 * -> AgentMemoryActionClassifier（动作分类）
 * -> AgentMemoryRuleExtractor + AgentMemoryLlmExtractor（双轨抽取）
 * -> AgentMemoryWritePolicy（写入策略校验）
 * -> AgentMemoryService.saveOrUpdateMemory（落库）
 * -> AgentMemoryContextService.rebuildProfile（画像重建）</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 AgentChatServiceImpl 在 Planner 执行前调用，捕获失败不影响主流程；</li>
 *   <li>规则抽取优先于 LLM 抽取，相同 key 不会被 LLM 覆盖，保证可解释性；</li>
 *   <li>ASK_MEMORY / NORMAL_CHAT 直接跳过写入，避免“你记得我叫什么吗”被误存为事实；</li>
 *   <li>DELETE_MEMORY 仅归档明确识别到 key 的记忆，未识别 key 时不执行删除，防止误删求职偏好；</li>
 *   <li>画像重建失败不回滚原始事实，因为派生摘要可由后台手动重建，原始事实不可恢复。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 第一版长期记忆只从工具结果沉淀，普通聊天里的“以后叫你xxx”不会入库。
 * 2. 第二版在用户消息入库后立即捕获自然语言事实，让称呼、偏好、目标能跨会话生效。
 * 3. 捕获结果仍然要走写入策略，避免把低价值、敏感或疑问句写成长期事实。</p>
 *
 * 作者: hfj
 * 日期: 2026/6/23
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
     * 从用户消息中捕获长期记忆，覆盖动作分类、双轨抽取、写入校验与画像重建全流程。
     *
     * <p>核心处理流程：
     * 1. 校验用户 ID 与消息非空，避免无效请求进入分类器；
     * 2. 调用动作分类器判定本轮输入属于 SET/UPDATE/DELETE/ASK/NORMAL_CHAT 中的哪一种；
     * 3. DELETE_MEMORY 仅归档明确识别到 key 的记忆，未识别 key 时直接跳过；
     * 4. ASK_MEMORY 与 NORMAL_CHAT 直接跳过写入，避免把疑问句或闲聊误存为长期事实；
     * 5. SET_MEMORY / UPDATE_MEMORY 才进入规则抽取 + LLM 抽取，按 memoryType:key 合并去重；
     * 6. 每条候选记忆必须通过写入策略校验后才落库，过滤低价值、敏感、置信度低的数据；
     * 7. 本轮只要写入或归档了记忆，就触发用户画像重建，保证后续 Prompt 上下文是最新的。</p>
     *
     * @param userId          当前求职用户唯一标识，用于绑定长期记忆归属
     * @param conversationId  当前会话 ID，作为记忆来源 conversationId 写入，便于溯源
     * @param traceId         当前链路 ID，用于跨组件日志关联与问题排查
     * @param message         已脱敏的用户输入文本，作为记忆抽取的原始语料
     * @return 本轮实际保存的记忆 VO 列表，未写入时返回空列表，调用方不可据此判断主流程成败
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

    /**
     * 处理 DELETE_MEMORY 动作，仅归档明确识别到 key 的记忆，未识别 key 时跳过。
     *
     * <p>核心处理流程：
     * 1. 校验分类器是否识别出目标 memoryKey，未识别时直接返回避免误删求职偏好；
     * 2. 调用 AgentMemoryService.archiveActiveMemoriesByKeys 批量归档有效记忆；
     * 3. 归档成功后触发画像重建，保证下次 Prompt 上下文不再包含已遗忘的事实。</p>
     *
     * @param userId          当前用户 ID
     * @param actionDecision  动作分类结果，包含识别到的目标 memoryKey 列表
     * @return 始终返回空列表，DELETE_MEMORY 不产生新的记忆 VO，仅用于表达“本轮未写入新事实”
     */
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

    /**
     * 合并规则抽取与 LLM 抽取的候选记忆，按 memoryType:key 去重且规则优先。
     *
     * @param ruleCandidates 规则抽取得到的候选记忆，可解释性强、召回有限
     * @param llmCandidates  LLM 抽取得到的候选记忆，召回更广但可能存在幻觉
     * @return 合并去重后的候选列表，相同 key 时保留规则抽取结果
     */
    private List<AgentMemoryCandidate> mergeCandidates(
            List<AgentMemoryCandidate> ruleCandidates,
            List<AgentMemoryCandidate> llmCandidates
    ) {
        Map<String, AgentMemoryCandidate> merged = new LinkedHashMap<>();
        putCandidates(merged, ruleCandidates);
        putCandidates(merged, llmCandidates);
        return new ArrayList<>(merged.values());
    }

    /**
     * 将候选记忆按 memoryType:key 写入合并 Map，已存在 key 时不覆盖（规则优先）。
     *
     * @param merged     合并后的目标 Map，key 为 memoryType:memoryKey
     * @param candidates 待合并的候选记忆列表，可为空
     */
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
