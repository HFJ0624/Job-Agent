package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentMemoryExtractionService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import com.job.enums.AgentPlanStatus;
import com.job.enums.AgentPlanStepStatus;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 计划执行服务实现，是 Agent 链路中 Tool Calling 与 Observation 的核心编排者。
 *
 * <p>核心职责：
 * 1. 加载计划与步骤，按 stepNo 顺序确定性执行，不依赖模型自主决策。
 * 2. 执行前通过条件更新抢占计划，防止同一计划被并发重复执行。
 * 3. 支持步骤级幂等：已完成的步骤直接采用历史结果，副作用工具不会重复执行。
 * 4. 支持失败恢复：recover=true 时把未完成步骤重置后从断点继续，已失败计划不会永久锁死。
 * 5. 对每一步执行 Guardrails 校验，防止计划外工具被越权调用。
 * 6. 通过 AgentToolInvoker 调用真实工具，并回写步骤状态（RUNNING/COMPLETED/FAILED/SKIPPED）。
 * 7. 任一步骤失败则终止后续步骤，并更新计划状态为 FAILED。
 * 8. 每一步都写入 Executor 观测事件（Observation），便于后台排障。
 * 9. 执行完成后调用 AgentMemoryExtractionService 沉淀长期记忆。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Service 层（Executor 阶段）。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.chat -> AgentPlanExecutorServiceImpl.executePlan
 * -> loadUserPlan / loadPlanSteps (加载计划)
 * -> executeToolStep (循环执行步骤)
 *    -> AgentGuardrailService.assertToolExecutionAllowed (Guardrails 校验)
 *    -> AgentToolInvoker.invoke (调用真实工具 + 写 Tool Trace)
 *    -> markStepCompleted / markStepFailed (回写步骤状态)
 *    -> AgentObservationService.recordEvent (写 Observation)
 * -> updatePlanStatus (更新计划状态)
 * -> AgentMemoryExtractionService.extractFromExecution (沉淀长期记忆)</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>AgentToolInvoker 统一调用工具，复用现有 Tool 实现，不绕过 Tool Guard 与 Tool Trace；</li>
 *   <li>AgentGuardrailService 校验工具是否在计划允许范围内，防止越权；</li>
 *   <li>AgentObservationService 记录 EXECUTOR 观测事件，与工具内部 TOOL 事件互补；</li>
 *   <li>AgentMemoryExtractionService 在执行完成后沉淀偏好与工具结果记忆；</li>
 *   <li>AgentRuntimeContext 通过 ThreadLocal 暴露 planId/stepId 给工具内部 Trace。</li>
 * </ul></p>
 *
 * <p>Agent 执行阶段说明：
 * 计划加载 -> 状态校验 -> 执行抢占（条件更新防并发） -> 步骤循环（已完成步骤采用历史结果；
 * 其余步骤走 Guardrails -> Tool Calling -> Observation -> 状态回写）
 * -> 计划状态更新 -> 长期记忆沉淀
 * 终止条件：所有步骤执行完毕，或某一步骤失败则跳过后续步骤。
 * 恢复能力：RUNNING 计划心跳超时判定为崩溃残留可重新抢占；FAILED 计划可显式恢复，
 * 从第一个未完成步骤继续，已 COMPLETED 步骤不重复调用工具。</p>
 *
 * 作者:hfj
 * 日期:2026/6/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanExecutorServiceImpl implements AgentPlanExecutorService {

    private static final int NOT_DELETED = 0;
    private static final int RESULT_PREVIEW_LENGTH = 1000;

    /**
     * RUNNING 计划的心跳超时时间（分钟）。
     *
     * 说明:
     * 1. Executor 每执行一个步骤都会刷新计划 update_time，作为执行心跳。
     * 2. 计划处于 RUNNING 但 update_time 超过该阈值，判定为上次进程崩溃留下的残留状态，
     *    允许新的请求重新抢占并继续执行，避免计划永远卡在执行中。
     * 3. 阈值需要大于单个步骤的最长合理执行时间（模型调用默认 45 秒超时），10 分钟是保守值。
     */
    private static final long RUNNING_STALE_MINUTES = 10;

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;
    private final AgentToolInvoker agentToolInvoker;
    private final AgentMemoryExtractionService agentMemoryExtractionService;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final AgentGuardrailService agentGuardrailService;
    private final AgentObservationService agentObservationService;
    private final ObjectMapper objectMapper;

    /**
     * 执行指定计划（默认不恢复失败计划）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanExecutionResult executePlan(Long userId, Long planId) {
        return executePlan(userId, planId, false);
    }

    /**
     * 执行指定计划，支持步骤级幂等与失败恢复。
     *
     * 方法步骤:
     * 1. 校验计划存在、属于当前用户，并且没有被逻辑删除。
     * 2. 已完成的计划直接返回历史结果；缺少参数的计划直接返回，不进入执行。
     * 3. 失败计划默认不重跑；只有 recover=true 时才允许恢复。
     * 4. RUNNING 计划在心跳超时内视为正在执行，拒绝重复提交；超时视为崩溃残留，允许抢占恢复。
     * 5. 通过条件更新抢占计划（PLANNED/FAILED/RUNNING -> RUNNING），抢占失败说明状态已被其他请求改变，
     *    本次不执行任何工具，直接返回最新状态对应结果。
     * 6. 恢复模式下把未完成步骤（FAILED/SKIPPED/残留 RUNNING）重置为 PENDING，从第一个未完成步骤继续。
     * 7. 循环执行时，已 COMPLETED 的步骤直接采用历史结果，不重复调用工具（步骤级幂等）。
     * 8. 读取计划步骤，按 stepNo 升序执行；对没有 toolName 的步骤标记 SKIPPED。
     * 9. 工具成功则步骤 COMPLETED；工具失败则步骤 FAILED，并终止后续步骤。
     * 10. 根据所有步骤结果更新计划状态为 COMPLETED 或 FAILED，返回结构化执行结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanExecutionResult executePlan(Long userId, Long planId, boolean recover) {
        AgentPlan plan = loadUserPlan(userId, planId);
        List<AgentPlanStep> steps = loadPlanSteps(planId);

        /*
         * 幂等保护:
         * 已完成的计划不再重复执行，避免重复生成评分、匹配、沟通记录等业务数据。
         */
        if (AgentPlanStatus.COMPLETED.name().equals(plan.getStatus())) {
            return buildExistingPlanResult(plan, steps, true, "计划已执行完成，无需重复执行");
        }

        /*
         * 参数不完整的计划不进入执行。
         * 正常情况下 ChatService 会先走澄清分支，这里只做防御，避免 Executor 被直接调用时缺少必填参数。
         */
        if (AgentPlanStatus.NEED_CLARIFICATION.name().equals(plan.getStatus())) {
            return buildExistingPlanResult(plan, steps, false, "计划仍缺少必要参数，请补充后重新发起");
        }

        /*
         * 失败计划默认不自动重跑，只有调用方显式要求恢复时才继续。
         */
        if (AgentPlanStatus.FAILED.name().equals(plan.getStatus()) && !recover) {
            return buildExistingPlanResult(plan, steps, false, "计划已执行失败，请重新发起任务");
        }

        /*
         * RUNNING 计划说明可能正在执行:
         * 1. update_time 在超时阈值内：认为是正常执行中的并发重复提交，直接拒绝，避免工具重复执行。
         * 2. update_time 超过超时阈值：认为是上次进程崩溃留下的残留状态，允许重新抢占恢复。
         */
        boolean runningPlan = AgentPlanStatus.RUNNING.name().equals(plan.getStatus());
        boolean staleRunning = runningPlan && isStaleRunning(plan);
        if (runningPlan && !staleRunning) {
            recordClaimRejectedEvent(plan, "CONCURRENT_EXECUTION", "计划正在执行中，拒绝重复提交");
            return buildExistingPlanResult(plan, steps, false, "计划正在执行中，请勿重复提交");
        }

        /*
         * 执行抢占: 通过条件更新把计划置为 RUNNING。
         * 影响行数为 0 说明计划状态在读取之后被其他请求改变（并发执行或已结束），
         * 本次不执行任何工具，直接返回最新状态对应的结果。
         */
        boolean recoveryMode = recover || staleRunning;
        List<String> claimableStatuses = new ArrayList<>();
        claimableStatuses.add(AgentPlanStatus.PLANNED.name());
        if (recoveryMode) {
            claimableStatuses.add(AgentPlanStatus.FAILED.name());
            claimableStatuses.add(AgentPlanStatus.RUNNING.name());
        }

        AgentPlan claim = new AgentPlan();
        claim.setStatus(AgentPlanStatus.RUNNING.name());
        claim.setUpdateTime(new Date());
        int claimedRows = agentPlanMapper.update(claim, new UpdateWrapper<AgentPlan>()
                .eq("id", plan.getId())
                .in("status", claimableStatuses));
        if (claimedRows <= 0) {
            recordClaimRejectedEvent(plan, "EXECUTION_CLAIM_REJECTED", "计划状态已变化，本次放弃执行");
            return buildCurrentStateResult(planId);
        }

        /*
         * 恢复模式: 把上次未完成的步骤重置为 PENDING，从第一个未完成步骤继续。
         * 已 COMPLETED 的步骤不重置，执行时会直接采用历史结果，保证副作用工具不重复执行。
         */
        if (recoveryMode) {
            resetIncompleteSteps(steps);
        }

        Map<String, Object> params = readJsonMap(plan.getExtractedParamsJson());

        List<AgentPlanStepExecutionResult> stepResults = new ArrayList<>();
        boolean failed = false;
        String failReason = null;

        for (AgentPlanStep step : steps) {
            if (failed) {
                stepResults.add(skipStep(plan, step, "前置步骤失败，跳过执行。"));
                continue;
            }

            /*
             * 步骤级幂等: 已完成的步骤直接采用历史结果，不重复调用工具。
             * 失败恢复后重跑计划时，这一步保证副作用工具不会再次执行业务写入。
             */
            if (AgentPlanStepStatus.COMPLETED.name().equals(step.getStatus())) {
                stepResults.add(adoptCompletedStep(plan, step));
                continue;
            }

            String toolName = chooseExecutableToolName(plan, step);
            if (!StringUtils.hasText(toolName)) {
                stepResults.add(skipStep(plan, step, "非工具步骤，已由 Planner/Executor 前置校验覆盖。"));
                continue;
            }

            AgentPlanStepExecutionResult result = executeToolStep(plan, step, toolName, params);
            stepResults.add(result);

            if (AgentPlanStepStatus.FAILED.name().equals(result.getStatus())) {
                failed = true;
                failReason = result.getErrorMsg();
            }
        }

        updatePlanStatus(plan, failed, failReason);

        AgentPlanExecutionResult executionResult = AgentPlanExecutionResult.builder()
                .planId(plan.getId())
                .success(!failed)
                .status(failed ? AgentPlanStatus.FAILED.name() : AgentPlanStatus.COMPLETED.name())
                .message(failed ? "计划执行失败: " + failReason : "计划执行完成")
                .steps(stepResults)
                .build();

        /*
         * 长期记忆沉淀放在计划状态更新之后:
         * 1. Executor 已经完成工具调用，工具结果和步骤状态都是确定的。
         * 2. 记忆提取失败不应该影响用户本轮对话，所以这里捕获异常并记录日志。
         * 3. 如果数据库还没有创建 agent_long_term_memory 表，本轮 Agent 仍然可以正常返回。
         */
        recordLongTermMemory(plan, executionResult);
        return executionResult;
    }

    private AgentPlan loadUserPlan(Long userId, Long planId) {
        if (planId == null) {
            throw new BizException("计划ID不能为空");
        }

        AgentPlan plan = agentPlanMapper.selectById(planId);
        if (plan == null || Integer.valueOf(1).equals(plan.getIsDeleted())) {
            throw new BizException("Agent 计划不存在");
        }
        if (!userId.equals(plan.getUserId())) {
            throw new BizException("Agent 计划不存在或无权限访问");
        }
        return plan;
    }

    private List<AgentPlanStep> loadPlanSteps(Long planId) {
        return agentPlanStepMapper.selectList(
                new LambdaQueryWrapper<AgentPlanStep>()
                        .eq(AgentPlanStep::getPlanId, planId)
                        .eq(AgentPlanStep::getIsDeleted, NOT_DELETED)
                        .orderByAsc(AgentPlanStep::getStepNo)
        );
    }

    private AgentPlanStepExecutionResult skipStep(AgentPlan plan, AgentPlanStep step, String reason) {
        step.setStatus(AgentPlanStepStatus.SKIPPED.name());
        step.setResultSummary(reason);
        step.setErrorMsg(null);
        step.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(step);
        recordExecutorEvent(plan, step, step.getToolName(), AgentObservationStatus.SKIPPED, null, reason, null, null);

        return AgentPlanStepExecutionResult.builder()
                .stepId(step.getId())
                .stepNo(step.getStepNo())
                .stepName(step.getStepName())
                .toolName(step.getToolName())
                .status(step.getStatus())
                .resultSummary(reason)
                .build();
    }

    private AgentPlanStepExecutionResult executeToolStep(
            AgentPlan plan,
            AgentPlanStep step,
            String toolName,
            Map<String, Object> params
    ) {
        markStepRunning(step, toolName);

        try {
            /*
             * 1. 把当前计划步骤放进 ThreadLocal。
             * 2. 真正工具执行时，AgentToolGuard 会把 planId/stepId 写进 Trace inputData。
             */
            AgentRuntimeContext.setCurrentPlanStep(plan.getId(), step.getId());

            /*
             * 3. 执行前做 Guardrails 校验。
             *    这里校验的是“Executor 实际要执行的工具”是否属于当前计划步骤允许范围。
             *    这一步不依赖模型自觉，可以防止计划外工具被越权调用。
             */
            agentGuardrailService.assertToolExecutionAllowed(
                    plan,
                    step,
                    toolName,
                    agentToolSchemaRegistry.getRequired(toolName)
            );

            /*
             * 4. 调用统一工具调用器。
             *    Invoker 内部会复用现有 Tool，因此原有业务 Service、Tool Guard、Tool Trace 都不会绕过。
             */
            AgentToolExecutionResult toolResult = agentToolInvoker.invoke(
                    toolName,
                    buildExecutionParams(params, plan),
                    plan.getUserGoal()
            );

            if (Boolean.TRUE.equals(toolResult.getSuccess())) {
                return markStepCompleted(plan, step, toolName, toolResult);
            }
            return markStepFailed(plan, step, toolName, toolResult);
        } catch (AgentToolException exception) {
            AgentToolExecutionResult toolResult = AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(exception.getToolErrorCode().name())
                    .message(exception.getMessage())
                    .costTime(0L)
                    .build();
            return markStepFailed(plan, step, toolName, toolResult);
        } catch (Exception exception) {
            AgentToolExecutionResult toolResult = AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED.name())
                    .message(exception.getMessage())
                    .costTime(0L)
                    .build();
            return markStepFailed(plan, step, toolName, toolResult);
        } finally {
            AgentRuntimeContext.clearCurrentPlanStep();
        }
    }

    private void markStepRunning(AgentPlanStep step, String toolName) {
        step.setToolName(toolName);
        step.setStatus(AgentPlanStepStatus.RUNNING.name());
        step.setErrorMsg(null);
        step.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(step);

        /*
         * 执行心跳: 每开始一个步骤就刷新计划的 update_time。
         * Executor 靠它判断 RUNNING 计划是正常执行中，还是进程崩溃后的残留状态。
         */
        touchPlanUpdateTime(step.getPlanId());
    }

    /**
     * 刷新计划 update_time，作为 Executor 的执行心跳。
     *
     * 说明:
     * 1. Executor 每开始一个步骤都会调用，靠它判断 RUNNING 计划是正常执行中还是崩溃残留。
     * 2. 这里使用字符串列名而不是 LambdaUpdateWrapper，因为在 Mock Mapper 的纯单元测试里，
     *    LambdaUpdateWrapper 需要 MyBatis-Plus 的 Lambda 缓存（只在 Spring 启动扫描 Mapper 时初始化），
     *    字符串列名生成的 SQL 完全等价，且不依赖该缓存，可测性更好。
     *
     * @param planId 计划ID
     */
    private void touchPlanUpdateTime(Long planId) {
        if (planId == null) {
            return;
        }
        agentPlanMapper.update(null, new UpdateWrapper<AgentPlan>()
                .eq("id", planId)
                .set("update_time", new Date()));
    }

    /**
     * 判断 RUNNING 计划是否已超过心跳超时时间。
     *
     * 超过阈值说明上次执行进程大概率已经崩溃（事务未提交，状态回滚不到旧值），
     * 计划会一直停留在 RUNNING，此时允许新的请求重新抢占。
     *
     * @param plan 当前计划
     * @return true 表示是崩溃残留状态
     */
    private boolean isStaleRunning(AgentPlan plan) {
        if (plan.getUpdateTime() == null) {
            return true;
        }
        long staleMillis = TimeUnit.MINUTES.toMillis(RUNNING_STALE_MINUTES);
        return System.currentTimeMillis() - plan.getUpdateTime().getTime() > staleMillis;
    }

    /**
     * 恢复模式下重置未完成步骤。
     *
     * 只重置 FAILED / SKIPPED / 残留 RUNNING 的步骤，已 COMPLETED 的步骤保持原状，
     * 执行时会走“采用历史结果”分支，保证副作用工具不会重复执行。
     *
     * @param steps 计划步骤列表
     */
    private void resetIncompleteSteps(List<AgentPlanStep> steps) {
        for (AgentPlanStep step : steps) {
            if (AgentPlanStepStatus.COMPLETED.name().equals(step.getStatus())) {
                continue;
            }

            step.setStatus(AgentPlanStepStatus.PENDING.name());
            step.setResultSummary(null);
            step.setErrorMsg(null);
            step.setUpdateTime(new Date());
            agentPlanStepMapper.updateById(step);
        }
    }

    /**
     * 采用已完成步骤的历史结果，不重复调用工具。
     *
     * 说明:
     * 1. 完整工具返回没有单独落库，这里采用步骤表里的结果摘要（有 1000 字符预览上限）。
     * 2. 结果会打上 idempotentHit 标记，Trace 和后台可以区分“真实执行”和“结果复用”。
     *
     * @param plan 当前计划
     * @param step 已完成的步骤
     * @return 步骤执行结果
     */
    private AgentPlanStepExecutionResult adoptCompletedStep(AgentPlan plan, AgentPlanStep step) {
        String summary = step.getResultSummary() == null ? "" : step.getResultSummary();

        AgentToolExecutionResult toolResult = AgentToolExecutionResult.builder()
                .success(true)
                .toolName(step.getToolName())
                .message("采用历史执行结果，未重复调用工具")
                .dataJson(summary)
                .idempotentHit(true)
                .costTime(0L)
                .build();

        recordExecutorEvent(
                plan,
                step,
                step.getToolName(),
                AgentObservationStatus.SUCCESS,
                null,
                "步骤已完成，采用历史结果（幂等命中）",
                null,
                toolResult
        );

        return AgentPlanStepExecutionResult.builder()
                .stepId(step.getId())
                .stepNo(step.getStepNo())
                .stepName(step.getStepName())
                .toolName(step.getToolName())
                .status(step.getStatus())
                .resultSummary(summary)
                .idempotentHit(true)
                .toolResult(toolResult)
                .build();
    }

    /**
     * 记录计划抢占被拒绝的观测事件。
     *
     * 并发重复提交或状态竞争时会走这里，方便后台看到“有哪些请求被幂等机制挡下”。
     *
     * @param plan 当前计划
     * @param errorCode 拒绝原因编码
     * @param message 拒绝原因描述
     */
    private void recordClaimRejectedEvent(AgentPlan plan, String errorCode, String message) {
        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(plan.getTraceId())
                .userId(plan.getUserId())
                .conversationId(plan.getConversationId())
                .planId(plan.getId())
                .intentCode(plan.getIntentCode())
                .eventType(AgentObservationEventType.EXECUTOR)
                .eventName("PlanExecutionClaim")
                .status(AgentObservationStatus.BLOCKED)
                .errorCode(errorCode)
                .errorMsg(message)
                .build());
    }

    /**
     * 抢占失败后重新读取计划最新状态，并返回对应结果。
     *
     * 抢占失败说明状态在读取之后被其他请求改变，此时内存里的 plan 已过期，
     * 必须重新查库，避免把旧状态当作最新状态返回给用户。
     *
     * @param planId 计划ID
     * @return 最新状态对应的执行结果
     */
    private AgentPlanExecutionResult buildCurrentStateResult(Long planId) {
        AgentPlan latestPlan = agentPlanMapper.selectById(planId);
        if (latestPlan == null) {
            throw new BizException("Agent 计划不存在");
        }

        List<AgentPlanStep> latestSteps = loadPlanSteps(planId);
        if (AgentPlanStatus.COMPLETED.name().equals(latestPlan.getStatus())) {
            return buildExistingPlanResult(latestPlan, latestSteps, true, "计划已执行完成，无需重复执行");
        }
        if (AgentPlanStatus.RUNNING.name().equals(latestPlan.getStatus())) {
            return buildExistingPlanResult(latestPlan, latestSteps, false, "计划正在执行中，请勿重复提交");
        }
        if (AgentPlanStatus.FAILED.name().equals(latestPlan.getStatus())) {
            return buildExistingPlanResult(latestPlan, latestSteps, false, "计划已执行失败，请重新发起任务");
        }
        if (AgentPlanStatus.NEED_CLARIFICATION.name().equals(latestPlan.getStatus())) {
            return buildExistingPlanResult(latestPlan, latestSteps, false, "计划仍缺少必要参数，请补充后重新发起");
        }
        return buildExistingPlanResult(latestPlan, latestSteps, false, "计划状态已变化，本次未执行工具");
    }

    private AgentPlanStepExecutionResult markStepCompleted(
            AgentPlan plan,
            AgentPlanStep step,
            String toolName,
            AgentToolExecutionResult toolResult
    ) {
        String summary = preview(toolResult.getDataJson());
        step.setToolName(toolName);
        step.setStatus(AgentPlanStepStatus.COMPLETED.name());
        step.setResultSummary(summary);
        step.setErrorMsg(null);
        step.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(step);
        recordExecutorEvent(plan, step, toolName, AgentObservationStatus.SUCCESS, null, summary, toolResult.getCostTime(), toolResult);

        return AgentPlanStepExecutionResult.builder()
                .stepId(step.getId())
                .stepNo(step.getStepNo())
                .stepName(step.getStepName())
                .toolName(toolName)
                .status(step.getStatus())
                .resultSummary(summary)
                .toolResult(toolResult)
                .build();
    }

    private AgentPlanStepExecutionResult markStepFailed(
            AgentPlan plan,
            AgentPlanStep step,
            String toolName,
            AgentToolExecutionResult toolResult
    ) {
        String errorMsg = toolResult.getMessage();
        step.setToolName(toolName);
        step.setStatus(AgentPlanStepStatus.FAILED.name());
        step.setResultSummary(null);
        step.setErrorMsg(errorMsg);
        step.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(step);
        recordExecutorEvent(
                plan,
                step,
                toolName,
                AgentObservationStatus.FAILED,
                toolResult.getErrorCode(),
                errorMsg,
                toolResult.getCostTime(),
                toolResult
        );

        return AgentPlanStepExecutionResult.builder()
                .stepId(step.getId())
                .stepNo(step.getStepNo())
                .stepName(step.getStepName())
                .toolName(toolName)
                .status(step.getStatus())
                .errorMsg(errorMsg)
                .toolResult(toolResult)
                .build();
    }

    private void updatePlanStatus(AgentPlan plan, boolean failed, String failReason) {
        plan.setStatus(failed ? AgentPlanStatus.FAILED.name() : AgentPlanStatus.COMPLETED.name());
        plan.setFailReason(failed ? failReason : null);
        plan.setUpdateTime(new Date());
        agentPlanMapper.updateById(plan);
    }

    private void recordLongTermMemory(AgentPlan plan, AgentPlanExecutionResult executionResult) {
        try {
            agentMemoryExtractionService.extractFromExecution(plan, executionResult);
        } catch (Exception exception) {
            log.warn(
                    "Agent 长期记忆提取失败，planId={}, userId={}, error={}",
                    plan == null ? null : plan.getId(),
                    plan == null ? null : plan.getUserId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 记录 Executor 步骤观测事件。
     *
     * 方法步骤:
     * 1. requestSnapshot 保存计划和步骤的关键上下文，方便后台知道当时准备执行什么。
     * 2. responseSnapshot 保存步骤结果或失败信息，方便和 TOOL 事件互相对照。
     * 3. 这里记录的是“编排层步骤事件”，工具内部仍然会通过 AgentTraceService 写 TOOL 事件。
     *
     * @param plan 当前计划
     * @param step 当前步骤
     * @param toolName 实际执行工具
     * @param status 观测状态
     * @param errorCode 错误码
     * @param message 结果或错误信息
     * @param durationMs 耗时
     * @param toolResult 工具执行结果
     */
    private void recordExecutorEvent(
            AgentPlan plan,
            AgentPlanStep step,
            String toolName,
            AgentObservationStatus status,
            String errorCode,
            String message,
            Long durationMs,
            AgentToolExecutionResult toolResult
    ) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("userGoal", plan.getUserGoal());
        requestSnapshot.put("planTitle", plan.getPlanTitle());
        requestSnapshot.put("stepNo", step.getStepNo());
        requestSnapshot.put("stepName", step.getStepName());
        requestSnapshot.put("stepGoal", step.getStepGoal());
        requestSnapshot.put("completionCriteria", step.getCompletionCriteria());

        Map<String, Object> responseSnapshot = new LinkedHashMap<>();
        responseSnapshot.put("message", message);
        responseSnapshot.put("toolResult", toolResult);

        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(plan.getTraceId())
                .userId(plan.getUserId())
                .conversationId(plan.getConversationId())
                .planId(plan.getId())
                .stepId(step.getId())
                .intentCode(plan.getIntentCode())
                .eventType(AgentObservationEventType.EXECUTOR)
                .eventName(resolveExecutorEventName(step, toolName))
                .status(status)
                .errorCode(errorCode)
                .errorMsg(AgentObservationStatus.FAILED.equals(status) ? message : null)
                .toolName(toolName)
                .durationMs(durationMs)
                .requestSnapshot(requestSnapshot)
                .responseSnapshot(responseSnapshot)
                .build());
    }

    private String resolveExecutorEventName(AgentPlanStep step, String toolName) {
        if (StringUtils.hasText(toolName)) {
            return toolName;
        }
        if (StringUtils.hasText(step.getStepName())) {
            return step.getStepName();
        }
        return "AgentExecutorStep";
    }

    private AgentPlanExecutionResult buildExistingPlanResult(
            AgentPlan plan,
            List<AgentPlanStep> steps,
            boolean success,
            String message
    ) {
        List<AgentPlanStepExecutionResult> stepResults = steps.stream()
                .map(step -> AgentPlanStepExecutionResult.builder()
                        .stepId(step.getId())
                        .stepNo(step.getStepNo())
                        .stepName(step.getStepName())
                        .toolName(step.getToolName())
                        .status(step.getStatus())
                        .resultSummary(step.getResultSummary())
                        .errorMsg(step.getErrorMsg())
                        .build())
                .toList();

        return AgentPlanExecutionResult.builder()
                .planId(plan.getId())
                .success(success)
                .status(plan.getStatus())
                .message(message)
                .steps(stepResults)
                .build();
    }

    private String chooseExecutableToolName(AgentPlan plan, AgentPlanStep step) {
        String toolName = step.getToolName();
        if (!StringUtils.hasText(toolName)) {
            return null;
        }

        if (!toolName.contains("/")) {
            return normalizeToolName(toolName.trim());
        }

        /*
         * 第一版只处理岗位搜索/推荐的二选一。
         * 用户明确说“推荐/适合/偏好”时使用推荐工具，否则使用普通搜索工具。
         */
        String goal = plan.getUserGoal() == null ? "" : plan.getUserGoal();
        if (goal.contains("推荐") || goal.contains("适合") || goal.contains("偏好")) {
            return normalizeToolName("JobRecommendTool.recommendJobs");
        }
        return normalizeToolName("JobSearchTool.searchJobs");
    }

    private String normalizeToolName(String toolName) {
        /*
         * Planner、Eval 或后台配置里可能仍然保存 ResumeAnalyzeTool 这类历史短名。
         * Executor 在真正执行前统一转成 ClassName.methodName，保证 Schema、Guardrails、Invoker 使用同一套工具名。
         */
        return agentToolSchemaRegistry.resolveToolName(toolName)
                .orElse(toolName);
    }

    private Map<String, Object> buildExecutionParams(Map<String, Object> params, AgentPlan plan) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (params != null) {
            merged.putAll(params);
        }
        merged.putIfAbsent("message", plan.getUserGoal());
        merged.putIfAbsent("query", plan.getUserGoal());
        return merged;
    }

    private Map<String, Object> readJsonMap(String json) {
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

    private String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= RESULT_PREVIEW_LENGTH
                ? text
                : text.substring(0, RESULT_PREVIEW_LENGTH) + "...";
    }
}
