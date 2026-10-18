package com.job.bootstrap.agent.compensation;

import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import com.job.enums.AgentPlanStepStatus;
import com.job.enums.AgentToolSideEffectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 功能: 计划补偿编排器（任务 G9，进程内 Saga 的补偿半边）。
 *
 * 设计说明:
 * 1. 触发时机：计划终失败（Replan 也救不了）时，由 Executor 调用；
 * 2. 补偿范围：只补“本轮真实执行成功”的步骤——失败恢复时采用的历史结果
 *    （idempotentHit）不在本轮职责内，它们的命运已在此前的尝试中决定；
 * 3. 顺序：按 stepNo 逆序（后写的先撤），符合 Saga 语义；
 * 4. 只读工具直接跳过（不记事件，避免观测噪音）；未注册补偿的写工具按
 *    NO_ACTION 记事件——“每个副作用步骤都要有明确交代，不允许静默”；
 * 5. 单步补偿失败不阻断后续补偿，全部记录到观测事件，最终汇总进失败消息，
 *    让用户与后台都能看到“哪些撤了、哪些撤不了、为什么”。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPlanCompensator {

    private final AgentCompensationRegistry compensationRegistry;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final AgentObservationService agentObservationService;

    /**
     * 逆序补偿本轮已完成的副作用步骤。
     *
     * @param plan        失败的计划
     * @param stepResults 本轮所有步骤执行结果
     * @return 补偿汇总（供 Executor 拼进失败消息）
     */
    public CompensationSummary compensate(AgentPlan plan, List<AgentPlanStepExecutionResult> stepResults) {
        List<AgentPlanStepExecutionResult> completedThisRun = stepResults.stream()
                .filter(result -> AgentPlanStepStatus.COMPLETED.name().equals(result.getStatus()))
                .filter(result -> !Boolean.TRUE.equals(result.getIdempotentHit()))
                .sorted(Comparator.comparing(AgentPlanStepExecutionResult::getStepNo).reversed())
                .toList();
        if (completedThisRun.isEmpty()) {
            return CompensationSummary.EMPTY;
        }

        int compensated = 0;
        int noAction = 0;
        int irreversible = 0;
        int failed = 0;
        for (AgentPlanStepExecutionResult result : completedThisRun) {
            String toolName = result.getToolName();
            if (isReadOnlyTool(toolName)) {
                continue; // 只读工具无副作用
            }

            Optional<AgentCompensationAction> action = compensationRegistry.find(toolName);
            CompensationKind kind = action.map(AgentCompensationAction::kind)
                    .orElse(CompensationKind.NO_ACTION);
            try {
                action.ifPresent(compensationAction ->
                        compensationAction.compensate(plan.getUserId(), plan.getId(), result));
                recordEvent(plan, result, kind, AgentObservationStatus.SUCCESS.name(), null);
                switch (kind) {
                    case COMPENSATABLE -> compensated++;
                    case IRREVERSIBLE -> irreversible++;
                    case NO_ACTION -> noAction++;
                }
            } catch (Exception exception) {
                log.warn("步骤补偿失败，planId={}, stepNo={}, toolName={}, error={}",
                        plan.getId(), result.getStepNo(), toolName, exception.getMessage());
                recordEvent(plan, result, kind, AgentObservationStatus.FAILED.name(), exception.getMessage());
                failed++;
            }
        }

        CompensationSummary summary = new CompensationSummary(compensated, noAction, irreversible, failed);
        log.info("计划补偿完成，planId={}, summary={}", plan.getId(), summary);
        return summary;
    }

    // 只读判定：Schema 明确 READ_ONLY 才算只读；Schema 缺失保守按写处理（走 NO_ACTION 事件）
    private boolean isReadOnlyTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        Optional<AgentToolSchema> schema = agentToolSchemaRegistry.find(toolName.trim());
        return schema.map(s -> AgentToolSideEffectType.READ_ONLY.equals(s.getSideEffectType())).orElse(false);
    }

    private void recordEvent(
            AgentPlan plan,
            AgentPlanStepExecutionResult result,
            CompensationKind kind,
            String status,
            String errorMsg
    ) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("stepNo", result.getStepNo());
        requestSnapshot.put("toolName", result.getToolName());
        requestSnapshot.put("kind", kind.name());
        if (errorMsg != null) {
            requestSnapshot.put("errorMsg", errorMsg);
        }

        try {
            agentObservationService.recordEvent(AgentObservationRecord.builder()
                    .traceId(plan.getTraceId())
                    .userId(plan.getUserId())
                    .conversationId(plan.getConversationId())
                    .planId(plan.getId())
                    .eventType(AgentObservationEventType.COMPENSATION)
                    .eventName("PlanCompensation")
                    .status(AgentObservationStatus.valueOf(status))
                    .requestSnapshot(requestSnapshot)
                    .build());
        } catch (Exception exception) {
            log.debug("补偿观测事件记录失败，planId={}, error={}", plan.getId(), exception.getMessage());
        }
    }

    /**
     * 补偿汇总。
     *
     * @param compensated 成功补偿的步骤数（执行了反向操作）
     * @param noAction    档案类副作用、明确不补偿的步骤数
     * @param irreversible 不可逆外部动作的步骤数
     * @param failed      补偿本身失败的步骤数
     */
    public record CompensationSummary(int compensated, int noAction, int irreversible, int failed) {

        public static final CompensationSummary EMPTY = new CompensationSummary(0, 0, 0, 0);

        public boolean hasActions() {
            return compensated + noAction + irreversible + failed > 0;
        }

        /** 人类可读汇总，拼进 Executor 失败消息。 */
        public String describe() {
            List<String> parts = new ArrayList<>();
            if (compensated > 0) {
                parts.add("已补偿 " + compensated + " 个副作用步骤");
            }
            if (irreversible > 0) {
                parts.add(irreversible + " 个动作不可逆");
            }
            if (failed > 0) {
                parts.add(failed + " 个补偿失败");
            }
            if (noAction > 0) {
                parts.add(noAction + " 个档案类步骤保留");
            }
            return String.join("，", parts);
        }
    }
}
