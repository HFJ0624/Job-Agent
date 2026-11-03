package com.job.bootstrap.agent.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.react.AgentResult;
import com.job.bootstrap.agent.react.AgentRunExecutionHooks;
import com.job.bootstrap.agent.run.AgentRunService;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.agent.AgentRun;
import com.job.common.vo.preference.UserJobPreferenceVO;
import com.job.enums.AgentRunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 功能: ReAct 岗位搜索入口（第一阶段，含验收修复 R2/R3/R5/R6）。
 *
 * 本轮修复要点:
 * 1. R2 状态分发（同一逻辑任务不新建重复 run）:
 *    RUNNING（心跳内）→ 返回“正在执行”，不新建；SUCCEEDED → 返回已持久化结果；
 *    WAITING_INPUT → 原 run 续跑；FAILED/过期 RUNNING → 仅显式重试（recover=true）且 CAS 成功后恢复；
 * 2. R2 路由顺序：先按已有任务（持久化引擎）路由，开关只影响新任务；
 * 3. R2 写回冲突：finish 返回 false 时不返回刚生成的成功答复；
 * 4. R3 条件合并：本轮显式 > 原 run.constraintsJson > 偏好，续跑把新条件写回；
 * 5. R5/R6 钩子：设置绝对截止时间与每轮消息检查点（短事务落库）；
 * 6. 归属校验：run.userId 与当前用户不一致时拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ReactSearchProperties.class)
public class JobSearchReactEntry {

    /** 每 run 总时限（毫秒）：与 AgentDefinition 默认 180 秒一致，供钩子使用。 */
    private static final long DEADLINE_MILLIS = 180_000L;

    private final ReactSearchProperties properties;
    private final AgentRunService agentRunService;
    private final JobSearchWorkflow jobSearchWorkflow;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final UserJobPreferenceService userJobPreferenceService;
    private final AgentTraceService agentTraceService;
    private final ObjectMapper objectMapper;

    /**
     * 入口结果。
     */
    public record Result(boolean handled, String answer, String status, Long runId,
                         JobSearchWorkflow.WorkflowOutcome outcome) {
    }

    /**
     * 是否由本入口接管（R2：先看已有任务，再看开关）。
     */
    public boolean shouldHandle(String intentCode, Long planId) {
        AgentRun existing = planId == null ? null : agentRunService.findLatestByPlan(planId);
        if (existing != null && JobSearchWorkflow.AGENT_NAME.equals(existing.getAgentName())) {
            return true;
        }
        return properties.isEnabled() && "JOB_SEARCH".equals(intentCode);
    }

    /** 兼容旧调用（无显式重试）。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String intentCode, String userMessage) {
        return execute(userId, conversationId, planId, traceId, intentCode, userMessage, false);
    }

    /**
     * 执行一次岗位搜索（模型调用与工具执行在事务外）。
     *
     * @param recover 显式重试（仅对 FAILED / 过期 RUNNING 生效）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String intentCode, String userMessage, boolean recover) {
        long start = System.currentTimeMillis();

        // 0. 已有任务：归属校验 + 状态分发（R2）
        // 优先按计划路由；首次请求没有 planId 时按会话找待续跑任务（前端只需带回 conversationId）
        AgentRun existing = planId == null ? null : agentRunService.findLatestByPlan(planId);
        if (existing == null && planId == null) {
            existing = agentRunService.findLatestResumableByConversation(conversationId, userId);
        }
        if (existing != null && !Objects.equals(existing.getUserId(), userId)) {
            return new Result(true, "任务不属于当前用户，已拒绝。", "REJECTED", existing.getId(), null);
        }

        AgentRun resumable = null;
        if (existing != null && JobSearchWorkflow.AGENT_NAME.equals(existing.getAgentName())) {
            String status = existing.getStatus();
            if (AgentRunStatus.RUNNING.name().equals(status)) {
                return new Result(true, "这个岗位搜索任务正在执行中，请稍候再试。",
                        "RUNNING", existing.getId(), null);
            }
            if (AgentRunStatus.SUCCEEDED.name().equals(status)) {
                return new Result(true, answerOf(existing), AgentRunStatus.SUCCEEDED.name(),
                        existing.getId(), null);
            }
            if (AgentRunStatus.WAITING_INPUT.name().equals(status)
                    || AgentRunStatus.PENDING.name().equals(status)) {
                resumable = existing;
            } else if (AgentRunStatus.FAILED.name().equals(status)) {
                if (!recover) {
                    return new Result(true, "该任务上次执行失败，如需重试请显式发起重试。",
                            AgentRunStatus.FAILED.name(), existing.getId(), null);
                }
                resumable = existing;
            }
        }

        // 1. 条件：本轮显式 > 原任务条件 > 长期偏好（R3）
        Map<String, Object> explicitParams = parameterExtractor.extract(userMessage);
        SearchConstraints previousConstraints = resumable == null ? null
                : readConstraints(resumable.getConstraintsJson());
        UserJobPreferenceVO preference = loadPreferenceSafely(userId);
        SearchConstraints constraints = SearchConstraints.merge(explicitParams, previousConstraints, preference);

        // 2. 创建或复用 run（同一逻辑任务不新建）
        boolean resume = resumable != null;
        AgentRun run = resume
                ? resumable
                : agentRunService.create(userId, conversationId, planId, traceId,
                        JobSearchWorkflow.AGENT_NAME, userMessage, toJson(constraints));

        // 3. 原子领取（FAILED 只在显式重试时允许）
        int expectedAttempt = run.getAttempt() == null ? 0 : run.getAttempt();
        boolean claimed = resume && AgentRunStatus.FAILED.name().equals(run.getStatus())
                ? agentRunService.claimForRetry(run.getId(), expectedAttempt)
                : agentRunService.claim(run.getId(), expectedAttempt);
        if (!claimed) {
            return new Result(true, "这个岗位搜索任务正在执行中，请稍候再试。",
                    "REJECTED", run.getId(), null);
        }
        int attempt = expectedAttempt + 1;

        // 4. 续跑：条件未变才复用旧候选，变化则失效
        boolean constraintsUnchanged = resume && previousConstraints != null
                && sameConstraints(previousConstraints, constraints);
        List<JobCandidates.JobCandidateItem> seed = constraintsUnchanged
                ? readCandidates(run.getResultJson())
                : List.of();

        AgentRunExecutionHooks.set(
                System.currentTimeMillis() + DEADLINE_MILLIS,
                messages -> agentRunService.saveMessages(run.getId(), attempt, toJson(messages))
        );
        try {
            List<AiChatMessage> resumeMessages = resume ? readMessages(run.getMessagesJson()) : null;
            Map<String, Object> previous = resume ? readMap(run.getResultJson()) : Map.of();
            int previousTurns = intOf(previous.get("turnsUsed"));
            int previousToolCalls = intOf(previous.get("toolCallsUsed"));

            JobSearchWorkflow.WorkflowOutcome outcome = jobSearchWorkflow.execute(
                    new JobSearchWorkflow.WorkflowInput(
                            userId, traceId, userMessage, constraints,
                            resumeMessages, previousTurns, previousToolCalls,
                            System.currentTimeMillis(),
                            conversationId, intentCode, seed
                    )
            );

            // 5. 状态映射 + 写回（持有权校验：冲突时不返回失去持有权的结果）
            AgentRunStatus status = switch (outcome.outcome()) {
                case FOUND, EMPTY -> AgentRunStatus.SUCCEEDED;
                case NEED_INPUT -> AgentRunStatus.WAITING_INPUT;
                case FAILED -> AgentRunStatus.FAILED;
            };
            boolean persisted = agentRunService.finish(run.getId(), attempt, status,
                    toJson(resultPayload(outcome, constraints, outcome.candidates())),
                    toJson(outcome.messages()),
                    outcome.outcome() == AgentResult.Outcome.FAILED ? outcome.reasonCode() : null);
            if (!persisted) {
                return new Result(true, "任务已被新的执行接管，本次结果已丢弃，请以最新状态为准。",
                        "CONFLICT", run.getId(), null);
            }

            saveTrace(traceId, userId, conversationId, intentCode, planId, run.getId(), userMessage,
                    constraints, outcome, System.currentTimeMillis() - start);

            return new Result(true, outcome.answer(), status.name(), run.getId(), outcome);
        } catch (Exception exception) {
            log.warn("ReAct 岗位搜索执行异常，runId={}, error={}", run.getId(), exception.getMessage());
            agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null, null,
                    "执行异常：" + exception.getMessage());
            return new Result(true, "岗位搜索执行失败：" + exception.getMessage(), "FAILED", run.getId(), null);
        } finally {
            AgentRunExecutionHooks.clear();
        }
    }

    // 结果载荷：含条件快照与合格候选（续跑复用）、预算计数、答复
    private Map<String, Object> resultPayload(JobSearchWorkflow.WorkflowOutcome outcome,
                                              SearchConstraints constraints,
                                              List<JobCandidates.JobCandidateItem> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcome", outcome.outcome().name());
        payload.put("reasonCode", outcome.reasonCode());
        payload.put("candidateIds", candidates.stream().map(JobCandidates.JobCandidateItem::jobId).toList());
        payload.put("candidates", candidates);
        payload.put("constraints", constraints);
        payload.put("missingFields", outcome.missingFields());
        payload.put("toolCallsUsed", outcome.toolCallsUsed());
        payload.put("turnsUsed", outcome.turnsUsed());
        payload.put("answer", outcome.answer());
        return payload;
    }

    private String answerOf(AgentRun run) {
        Map<String, Object> payload = readMap(run.getResultJson());
        Object answer = payload.get("answer");
        return answer == null ? "任务已完成。" : String.valueOf(answer);
    }

    private SearchConstraints readConstraints(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SearchConstraints.class);
        } catch (Exception exception) {
            log.warn("历史条件解析失败，按无条件处理，error={}", exception.getMessage());
            return null;
        }
    }

    private List<JobCandidates.JobCandidateItem> readCandidates(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return List.of();
        }
        try {
            Map<String, Object> payload = readMap(resultJson);
            Object candidates = payload.get("candidates");
            if (candidates == null) {
                return List.of();
            }
            return objectMapper.convertValue(candidates,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, JobCandidates.JobCandidateItem.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    // 条件是否一致（城市/薪资/学历/目标方向/关键词）
    private boolean sameConstraints(SearchConstraints left, SearchConstraints right) {
        return Objects.equals(left.city(), right.city())
                && Objects.equals(left.minSalary(), right.minSalary())
                && Objects.equals(left.educationReq(), right.educationReq())
                && Objects.equals(left.targetRole(), right.targetRole())
                && Objects.equals(left.keyword(), right.keyword());
    }

    private void saveTrace(String traceId, Long userId, Long conversationId, String intentCode, Long planId,
                           Long runId, String userMessage, SearchConstraints constraints,
                           JobSearchWorkflow.WorkflowOutcome outcome, long costTime) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("message", userMessage);
            input.put("runId", runId);
            input.put("planId", planId);
            input.put("constraints", constraints);
            input.put("executionMode", "WORKFLOW_REACT");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("outcome", outcome.outcome().name());
            output.put("reasonCode", outcome.reasonCode());
            output.put("candidateIds", outcome.candidates().stream()
                    .map(JobCandidates.JobCandidateItem::jobId).toList());
            output.put("toolCallsUsed", outcome.toolCallsUsed());
            output.put("turnsUsed", outcome.turnsUsed());

            agentTraceService.saveTrace(
                    traceId, userId, conversationId,
                    StringUtils.hasText(intentCode) ? intentCode : "JOB_SEARCH",
                    "JobSearchWorkflow", input, output,
                    outcome.outcome() == AgentResult.Outcome.FAILED ? "FAILED" : "SUCCESS",
                    outcome.reasonCode(), costTime);
        } catch (Exception exception) {
            log.debug("ReAct 岗位搜索 Trace 写入失败，traceId={}, error={}", traceId, exception.getMessage());
        }
    }

    private UserJobPreferenceVO loadPreferenceSafely(Long userId) {
        try {
            return userJobPreferenceService.getPreference(userId);
        } catch (Exception exception) {
            log.debug("求职偏好加载失败，按无偏好处理，userId={}, error={}", userId, exception.getMessage());
            return null;
        }
    }

    private List<AiChatMessage> readMessages(String messagesJson) {
        if (!StringUtils.hasText(messagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(messagesJson, new TypeReference<List<AiChatMessage>>() {
            });
        } catch (Exception exception) {
            log.warn("run 消息序列解析失败，按新 run 处理，error={}", exception.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
