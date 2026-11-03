package com.job.bootstrap.agent.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.react.AgentResult;
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

/**
 * 功能: ReAct 岗位搜索入口（第一阶段）。
 *
 * 关键设计（对应文档“入口与兼容”“状态与结果保存”）:
 * 1. **事务边界**：本方法标注 NOT_SUPPORTED——挂起调用方（chatInternal 带 @Transactional）的事务，
 *    模型调用与工具执行在事务外；run 的创建/写回由 AgentRunService 的短事务完成。
 *    （只在 Runner 内部拆事务、外层仍包住整条链路，不算完成——文档明确要求。）
 * 2. **开关**：job.agent.react-search.enabled 默认 false；关闭时完全不接管。
 * 3. **路由**：新任务按当前意图（JOB_SEARCH）；携带已有 planId 的请求优先按所属 run 路由（续跑），
 *    不重新用当前消息分类。
 * 4. **续跑**：WAITING_INPUT 的 run 复用其完整消息序列（含 tool 结果），追加本轮输入后继续；
 *    条件变化时旧候选自然失效（结果验真只用本轮真实工具结果）。
 * 5. 语义缓存旁路：本入口在缓存查询之前接管，ReAct 任务不会被旧答案短路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ReactSearchProperties.class)
public class JobSearchReactEntry {

    private final ReactSearchProperties properties;
    private final AgentRunService agentRunService;
    private final JobSearchWorkflow jobSearchWorkflow;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final UserJobPreferenceService userJobPreferenceService;
    private final AgentTraceService agentTraceService;
    private final ObjectMapper objectMapper;

    /**
     * 入口结果。
     *
     * @param handled  是否由本入口接管
     * @param answer   对外答复（handled=true 时非空）
     * @param status   SUCCEEDED / WAITING_INPUT / FAILED / REJECTED
     * @param runId    本次 run ID
     * @param outcome  Workflow 结论（可能为 null）
     */
    public record Result(boolean handled, String answer, String status, Long runId,
                         JobSearchWorkflow.WorkflowOutcome outcome) {
    }

    private static final Result NOT_HANDLED = new Result(false, null, null, null, null);

    /**
     * 是否由本入口接管：开关开启 +（当前意图是岗位搜索 或 已有计划的 run 属于本 Agent）。
     */
    public boolean shouldHandle(String intentCode, Long planId) {
        if (!properties.isEnabled()) {
            return false;
        }
        if ("JOB_SEARCH".equals(intentCode)) {
            return true;
        }
        // 续跑/补参：按所属任务路由，不看当前消息分类
        if (planId != null) {
            AgentRun existing = agentRunService.findLatestByPlan(planId);
            return existing != null && JobSearchWorkflow.AGENT_NAME.equals(existing.getAgentName());
        }
        return false;
    }

    /**
     * 执行一次岗位搜索（模型调用与工具执行在事务外）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String intentCode, String userMessage) {
        long start = System.currentTimeMillis();

        // 1. 条件快照：本轮显式 > 长期偏好
        Map<String, Object> explicitParams = parameterExtractor.extract(userMessage);
        UserJobPreferenceVO preference = loadPreferenceSafely(userId);
        SearchConstraints constraints = SearchConstraints.merge(explicitParams, preference);

        // 2. 续跑判定：同计划下存在 WAITING_INPUT 的 run
        AgentRun resumeRun = planId == null ? null : agentRunService.findLatestByPlan(planId);
        boolean resume = resumeRun != null
                && AgentRunStatus.WAITING_INPUT.name().equals(resumeRun.getStatus())
                && JobSearchWorkflow.AGENT_NAME.equals(resumeRun.getAgentName());

        AgentRun run;
        if (resume) {
            run = resumeRun;
        } else {
            run = agentRunService.create(userId, conversationId, planId, traceId,
                    JobSearchWorkflow.AGENT_NAME, userMessage, toJson(constraints));
        }

        int expectedAttempt = run.getAttempt() == null ? 0 : run.getAttempt();
        if (!agentRunService.claim(run.getId(), expectedAttempt)) {
            // 重复请求：已被其他执行器领取
            return new Result(true, "这个岗位搜索任务正在执行中，请稍候再试。",
                    "REJECTED", run.getId(), null);
        }
        int attempt = expectedAttempt + 1;

        try {
            /*
             * 3. 续跑：恢复完整消息序列（含 tool 结果）与已用轮次/工具次数。
             * 预算说明：补参是新一轮用户交互，截止时间重新计时（避免把用户思考时间算进模型预算）；
             * 已用轮次/工具次数延续，防止续跑变成“预算重置绕过”。
             */
            List<AiChatMessage> resumeMessages = resume ? readMessages(run.getMessagesJson()) : null;
            Map<String, Object> previous = resume ? readMap(run.getResultJson()) : Map.of();
            int previousTurns = intOf(previous.get("turnsUsed"));
            int previousToolCalls = intOf(previous.get("toolCallsUsed"));
            long startedAt = System.currentTimeMillis();

            JobSearchWorkflow.WorkflowOutcome outcome = jobSearchWorkflow.execute(
                    new JobSearchWorkflow.WorkflowInput(
                            userId,
                            traceId,
                            userMessage,
                            constraints,
                            resumeMessages,
                            previousTurns,
                            previousToolCalls,
                            startedAt
                    )
            );

            // 4. 状态映射 + 写回（持有权校验）
            AgentRunStatus status = switch (outcome.outcome()) {
                case FOUND, EMPTY -> AgentRunStatus.SUCCEEDED;
                case NEED_INPUT -> AgentRunStatus.WAITING_INPUT;
                case FAILED -> AgentRunStatus.FAILED;
            };
            String messagesJson = toJson(outcome.messages());
            boolean persisted = agentRunService.finish(run.getId(), attempt, status,
                    toJson(resultPayload(outcome)), messagesJson,
                    outcome.outcome() == AgentResult.Outcome.FAILED ? outcome.reasonCode() : null);
            if (!persisted) {
                log.warn("run 写回被持有权校验拒绝，runId={}, attempt={}", run.getId(), attempt);
            }

            // 5. Trace：链路与 run/turn/toolCalls 关联
            saveTrace(traceId, userId, conversationId, intentCode, planId, run.getId(), userMessage,
                    constraints, outcome, System.currentTimeMillis() - start);

            return new Result(true, outcome.answer(), status.name(), run.getId(), outcome);
        } catch (Exception exception) {
            log.warn("ReAct 岗位搜索执行异常，runId={}, error={}", run.getId(), exception.getMessage());
            agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null, null,
                    "执行异常：" + exception.getMessage());
            return new Result(true, "岗位搜索执行失败：" + exception.getMessage(), "FAILED", run.getId(), null);
        }
    }

    private Map<String, Object> resultPayload(JobSearchWorkflow.WorkflowOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("outcome", outcome.outcome().name());
        payload.put("reasonCode", outcome.reasonCode());
        payload.put("candidateIds", outcome.candidates().stream()
                .map(JobCandidates.JobCandidateItem::jobId).toList());
        payload.put("missingFields", outcome.missingFields());
        payload.put("toolCallsUsed", outcome.toolCallsUsed());
        payload.put("turnsUsed", outcome.turnsUsed());
        return payload;
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
