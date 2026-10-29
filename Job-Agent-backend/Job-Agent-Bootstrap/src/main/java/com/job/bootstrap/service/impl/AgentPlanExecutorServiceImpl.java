package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.compensation.AgentPlanCompensator;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.plan.AgentReplanner;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentMemoryExtractionService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import com.job.enums.AgentPlanStatus;
import com.job.enums.AgentPlanStepStatus;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolSideEffectType;
import com.job.exception.AgentToolException;
import com.job.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 计划执行服务实现，是 Agent 链路中 Tool Calling 与 Observation 的核心编排者。
 *
 * <p>核心职责：
 * 1. 加载计划与步骤，按 stepNo 顺序确定性执行，不依赖模型自主决策。
 * 2. 执行前通过条件更新抢占计划，防止同一计划被并发重复执行。
 * 3. 支持步骤级幂等：已完成的步骤直接采用历史结果，副作用工具不会重复执行。
 * 4. 支持失败恢复：recover=true 时把未完成步骤重置后从断点继续，已失败计划不会永久锁死。
 * 5. 支持并行执行（任务 G4）：只读步骤按依赖分层并行（写副作用步骤是串行屏障，绝不并发写业务数据）。
 * 6. 支持补偿事务（任务 G9）：计划终失败时，对本轮已完成的副作用步骤按 stepNo 逆序补偿
 *    （可补偿动作幂等执行；不可逆动作记录并如实告知；档案类副作用明确保留）。
 * 5. 对每一步执行 Guardrails 校验，防止计划外工具被越权调用。
 * 6. 通过 AgentToolInvoker 调用真实工具，并回写步骤状态（RUNNING/COMPLETED/FAILED/SKIPPED）。
 * 7. 任一步骤失败则终止后续步骤，并更新计划状态为 FAILED；失败且还有预算时先尝试 Replan 重规划（任务 G2）。
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

    /**
     * 单个计划最多允许的重规划次数（任务 G2 Replan）。
     * 步骤失败时会尝试让 Replanner 给出替代步骤；次数用尽后按原行为失败，防止无限 replan。
     */
    private static final int MAX_REPLAN_PER_PLAN = 2;

    /**
     * 步骤间数据传递占位符：{{step_N.output.xxx}}。
     * N 为步骤序号，xxx 为该步工具输出里的字段路径（支持点号多级与数组下标 [i]，
     * 例如 {{step_2.output.jobs[0].jobId}}），缺省 output 表示整个输出。
     */
    private static final Pattern STEP_OUTPUT_PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*step_(\\d+)\\.output((?:\\.[\\w.]+|\\[\\d+\\])*)\\s*\\}\\}");

    /** 占位符路径里的“字段[下标]”片段，例如 jobs[0]。 */
    private static final Pattern INDEXED_FIELD = Pattern.compile("([\\w.]+)\\[(\\d+)\\]");

    /** 占位符路径里的“裸下标”片段，例如 [0]（输出本身是数组时使用）。 */
    private static final Pattern BARE_INDEX = Pattern.compile("\\[(\\d+)\\]");

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;
    private final AgentToolInvoker agentToolInvoker;
    private final AgentMemoryExtractionService agentMemoryExtractionService;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final AgentGuardrailService agentGuardrailService;
    private final AgentObservationService agentObservationService;
    private final AgentReplanner agentReplanner;
    private final AgentPlanCompensator agentPlanCompensator;
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
        // 可变副本：Replan 会在执行过程中追加步骤（不依赖 Mapper 返回集合的可变性）
        List<AgentPlanStep> steps = new ArrayList<>(loadPlanSteps(planId));

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
        // 步骤输出快照：stepNo -> 该步工具输出（解析后的 JSON），供后续步骤的 {{step_N.output.xxx}} 占位符引用
        // G4：并行执行下多线程写快照，用 ConcurrentHashMap
        Map<Integer, Object> stepOutputs = new ConcurrentHashMap<>();
        // 已解析步骤序号（本轮已执行 + 已完成采用历史结果），用于依赖就绪判断
        Set<Integer> resolvedStepNos = ConcurrentHashMap.newKeySet();

        // 已 COMPLETED 的步骤直接采用历史结果（步骤级幂等），并把历史摘要尽力播种进输出快照，供占位符引用
        List<AgentPlanStep> pending = new ArrayList<>();
        for (AgentPlanStep step : steps) {
            if (AgentPlanStepStatus.COMPLETED.name().equals(step.getStatus())) {
                stepResults.add(adoptCompletedStep(plan, step));
                resolvedStepNos.add(step.getStepNo());
                seedOutputFromSummary(stepOutputs, step);
                continue;
            }
            pending.add(step);
        }

        /*
         * G4 并行执行主循环（按“波次”执行）:
         * 1. 依赖只认 {{step_N.output.xxx}} 引用（N < 当前 stepNo），满足才就绪；
         * 2. 写副作用工具（非 READ_ONLY，Schema 缺失保守视为写）是串行屏障：单独成波、按 stepNo 保序，绝不并发；
         * 3. 只读步骤在下一个写步骤之前的窗口内并行执行（有界线程池，池满由提交线程兜底）；
         * 4. 任一波次有步骤失败 → 第一个失败步骤走 G2 重规划；成功则同波其他失败步骤随改道放弃，替代步骤加入 pending 继续。
         */
        boolean failed = false;
        String failReason = null;
        // 补偿汇总后缀（任务 G9）：终失败时补偿完成才拼接，成功路径为空
        String compensationSuffix = "";
        while (!pending.isEmpty() && !failed) {
            List<AgentPlanStep> ready = pending.stream()
                    .filter(step -> depsSatisfied(step, resolvedStepNos))
                    .toList();
            if (ready.isEmpty()) {
                // 防御：依赖无法解析（正常不会发生，依赖只引用更小 stepNo）→ 放弃剩余步骤
                for (AgentPlanStep step : pending) {
                    stepResults.add(skipStep(plan, step, "步骤依赖无法解析，跳过执行。"));
                }
                break;
            }

            List<AgentPlanStep> wave = chooseWave(ready, pending);
            List<AgentPlanStepExecutionResult> waveResults = executeWave(plan, wave, params, stepOutputs);
            // 按 stepNo 排序，保证无论并行快慢，结果顺序确定
            waveResults.sort(Comparator.comparing(AgentPlanStepExecutionResult::getStepNo));
            pending.removeAll(wave);

            // 输出快照与已解析序号在主线程汇总，避免并发写竞争
            for (AgentPlanStepExecutionResult result : waveResults) {
                stepResults.add(result);
                rememberStepOutput(stepOutputs, result);
                resolvedStepNos.add(result.getStepNo());
            }

            List<AgentPlanStepExecutionResult> failedResults = waveResults.stream()
                    .filter(result -> AgentPlanStepStatus.FAILED.name().equals(result.getStatus()))
                    .toList();
            if (!failedResults.isEmpty()) {
                AgentPlanStepExecutionResult firstFailedResult = failedResults.get(0);
                AgentPlanStep firstFailedStep = wave.stream()
                        .filter(step -> Objects.equals(step.getStepNo(), firstFailedResult.getStepNo()))
                        .findFirst()
                        .orElseThrow();
                List<AgentPlanStep> correctiveSteps = tryReplan(plan, firstFailedStep, firstFailedResult, steps);
                if (correctiveSteps.isEmpty()) {
                    failed = true;
                    failReason = firstFailedResult.getErrorMsg();
                } else {
                    // 同波次其他失败步骤：随重规划改道放弃（标记 SKIPPED，避免遗留 FAILED 状态）
                    for (AgentPlanStepExecutionResult other : failedResults.subList(1, failedResults.size())) {
                        AgentPlanStep otherStep = wave.stream()
                                .filter(step -> Objects.equals(step.getStepNo(), other.getStepNo()))
                                .findFirst()
                                .orElse(null);
                        if (otherStep != null) {
                            stepResults.add(skipStep(plan, otherStep, "同波次步骤失败，随 Replan 改道放弃。"));
                        }
                    }
                    pending.addAll(correctiveSteps);
                }
            }
        }

        // 失败终止后，剩余未执行步骤统一标记 SKIPPED，语义与改造前一致
        if (failed) {
            for (AgentPlanStep step : pending) {
                stepResults.add(skipStep(plan, step, "前置步骤失败，跳过执行。"));
            }

            /*
             * 任务 G9：补偿事务——计划终失败时，对本轮已完成的副作用步骤按 stepNo 逆序补偿
             * （后写的先撤）。补偿汇总拼进失败消息，让用户明确知道“哪些撤了、哪些撤不了”。
             * 补偿内部单步失败不抛出，绝不因为补偿失败而掩盖原始失败原因。
             */
            AgentPlanCompensator.CompensationSummary compensation =
                    agentPlanCompensator.compensate(plan, stepResults);
            if (compensation.hasActions()) {
                compensationSuffix = "（" + compensation.describe() + "）";
            }
        }

        updatePlanStatus(plan, failed, failReason);

        AgentPlanExecutionResult executionResult = AgentPlanExecutionResult.builder()
                .planId(plan.getId())
                .success(!failed)
                .status(failed ? AgentPlanStatus.FAILED.name() : AgentPlanStatus.COMPLETED.name())
                .message(failed
                        ? "计划执行失败: " + failReason + compensationSuffix
                        : "计划执行完成")
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

    /**
     * 尝试重规划（任务 G2）：有预算且 Replanner 给出替代步骤时，放弃失败步骤并追加替代步骤。
     *
     * <p>与“失败恢复”的区别：恢复是重跑同一步骤；重规划是换路径——失败步骤标记 SKIPPED，
     * 由 LLM 基于已完成步骤的上下文给出替代步骤，追加到计划尾部继续执行。</p>
     *
     * @param plan         当前计划
     * @param failedStep   失败的步骤
     * @param failedResult 失败步骤的执行结果
     * @param steps        当前步骤列表（会被追加替代步骤）
     * @return 追加的替代步骤；空表示不重规划，调用方按失败处理
     */
    private List<AgentPlanStep> tryReplan(
            AgentPlan plan,
            AgentPlanStep failedStep,
            AgentPlanStepExecutionResult failedResult,
            List<AgentPlanStep> steps
    ) {
        int replanCount = plan.getReplanCount() == null ? 0 : plan.getReplanCount();
        if (replanCount >= MAX_REPLAN_PER_PLAN) {
            log.info("Agent 计划重规划次数用尽，按失败处理，planId={}, replanCount={}", plan.getId(), replanCount);
            return List.of();
        }

        List<AgentReplanner.ReplanStep> proposals = agentReplanner.replan(
                plan.getUserId(),
                plan.getTraceId(),
                plan.getUserGoal(),
                buildCompletedStepsSummary(steps),
                failedStep.getStepName(),
                failedResult.getErrorMsg()
        );
        if (proposals.isEmpty()) {
            log.info("Agent 重规划无可行替代步骤，按失败处理，planId={}", plan.getId());
            return List.of();
        }

        // 1. 失败步骤放弃：标记 SKIPPED，由替代步骤接管目标
        failedStep.setStatus(AgentPlanStepStatus.SKIPPED.name());
        failedStep.setResultSummary("执行失败，已由 Replan 替代步骤接管");
        failedStep.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(failedStep);

        // 2. 替代步骤追加到计划尾部（stepNo 续增），随即参与本轮执行
        List<AgentPlanStep> appended = appendReplanSteps(plan, steps, proposals);

        // 3. 计数 +1 落库（条件更新避免覆盖并发修改），供下次触发判定与后台审计
        plan.setReplanCount(replanCount + 1);
        agentPlanMapper.update(null, new UpdateWrapper<AgentPlan>()
                .eq("id", plan.getId())
                .set("replan_count", replanCount + 1));

        // 4. 观测事件：后台可统计“失败→改道”的发生次数
        recordReplanEvent(plan, failedStep, proposals.size(), replanCount + 1);
        return appended;
    }

    // 把替代步骤插入计划（stepNo 续增），返回插入后的实体列表
    private List<AgentPlanStep> appendReplanSteps(AgentPlan plan, List<AgentPlanStep> steps, List<AgentReplanner.ReplanStep> proposals) {
        Date now = new Date();
        int nextStepNo = steps.stream()
                .map(AgentPlanStep::getStepNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        List<AgentPlanStep> appended = new ArrayList<>();
        for (AgentReplanner.ReplanStep proposal : proposals) {
            AgentPlanStep step = new AgentPlanStep();
            step.setPlanId(plan.getId());
            step.setUserId(plan.getUserId());
            step.setConversationId(plan.getConversationId());
            step.setStepNo(++nextStepNo);
            step.setStepName(proposal.stepName());
            step.setStepGoal(proposal.stepGoal());
            step.setToolName(proposal.toolName());
            step.setToolInputSchema(toJson(buildReplanInputSchema(proposal)));
            step.setCompletionCriteria(proposal.completionCriteria());
            step.setStatus(AgentPlanStepStatus.PENDING.name());
            step.setIsDeleted(NOT_DELETED);
            step.setCreateTime(now);
            step.setUpdateTime(now);
            agentPlanStepMapper.insert(step);
            appended.add(step);
        }
        return appended;
    }

    // 与 LLM 补步同一格式：extractedParams 供 Executor 读取并解析 {{step_N.output.xxx}} 占位符
    private Map<String, Object> buildReplanInputSchema(AgentReplanner.ReplanStep proposal) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("extractedParams", proposal.toolInput());
        schema.put("source", "LLM_REPLAN");
        schema.put("note", "Replan 替代步骤的可执行参数，Executor 会解析其中的步骤间数据占位符。");
        return schema;
    }

    // 已完成步骤摘要（含 stepNo，供 Replanner 用 {{step_N.output.xxx}} 引用前序输出）
    private String buildCompletedStepsSummary(List<AgentPlanStep> steps) {
        StringBuilder builder = new StringBuilder();
        for (AgentPlanStep step : steps) {
            if (!AgentPlanStepStatus.COMPLETED.name().equals(step.getStatus())) {
                continue;
            }
            builder.append("步骤").append(step.getStepNo())
                    .append("（").append(StringUtils.hasText(step.getStepName()) ? step.getStepName() : "未命名").append("）: ")
                    .append(preview(step.getResultSummary()))
                    .append('\n');
        }
        return builder.toString();
    }

    // 重规划观测事件：记录哪个步骤失败、改道出了几个替代步骤、这是第几次重规划
    private void recordReplanEvent(AgentPlan plan, AgentPlanStep failedStep, int appendedCount, int replanCount) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("failedStepNo", failedStep.getStepNo());
        requestSnapshot.put("failedStepName", failedStep.getStepName());
        requestSnapshot.put("appendedSteps", appendedCount);
        requestSnapshot.put("replanCount", replanCount);

        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(plan.getTraceId())
                .userId(plan.getUserId())
                .conversationId(plan.getConversationId())
                .planId(plan.getId())
                .intentCode(plan.getIntentCode())
                .eventType(AgentObservationEventType.EXECUTOR)
                .eventName("PlanReplan")
                .status(AgentObservationStatus.SUCCESS)
                .requestSnapshot(requestSnapshot)
                .build());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.debug("序列化 JSON 失败，error={}", exception.getMessage());
            return "{}";
        }
    }

    /*
     * 选波规则（任务 G4）:
     * 1. ready = 依赖已满足的待执行步骤；
     * 2. 写副作用步骤（非 READ_ONLY，Schema 缺失保守视为写）是串行屏障：永远单独成波、按 stepNo 保序，
     *    绝不两个写并发，也绝不允许写与任何步骤并发——并行只读不可能写业务数据，从根上避免死锁与业务竞争；
     * 3. 只读步骤在“下一个写步骤之前”的窗口内并行；没有写步骤时全部 ready 一步并行。
     */
    private List<AgentPlanStep> chooseWave(List<AgentPlanStep> ready, List<AgentPlanStep> pending) {
        Integer nextWriteNo = pending.stream()
                .filter(step -> !isReadOnlyTool(step))
                .map(AgentPlanStep::getStepNo)
                .min(Integer::compareTo)
                .orElse(null);

        List<AgentPlanStep> window = nextWriteNo == null
                ? ready
                : ready.stream().filter(step -> step.getStepNo() < nextWriteNo).toList();
        if (!window.isEmpty()) {
            return window;
        }

        // 写之前没有可并行的只读步骤：ready 里有写就单独执行最小 stepNo 的写（屏障保序），否则全部并行
        Optional<AgentPlanStep> readyWrite = ready.stream()
                .filter(step -> !isReadOnlyTool(step))
                .min(Comparator.comparing(AgentPlanStep::getStepNo));
        if (readyWrite.isPresent()) {
            return List.of(readyWrite.get());
        }
        return ready;
    }

    // 执行一个波次：单步直接执行；多步提交有界线程池并行，结果按 stepNo 排序保证确定性
    private List<AgentPlanStepExecutionResult> executeWave(
            AgentPlan plan,
            List<AgentPlanStep> wave,
            Map<String, Object> params,
            Map<Integer, Object> stepOutputs
    ) {
        if (wave.size() == 1) {
            return new ArrayList<>(List.of(runStep(plan, wave.get(0), params, stepOutputs)));
        }

        recordWaveEvent(plan, wave);

        /*
         * 并行执行的关键细节（G4）：AgentRuntimeContext 是 ThreadLocal，工作线程里没有主线程的上下文，
         * setCurrentPlanStep 会直接抛“运行时上下文不存在”。这里提交前快照主线程上下文，
         * 工作线程里重建自己的副本（planId/stepId 每线程独立，避免并行步骤互相覆盖），任务结束清理。
         */
        AgentRuntimeContext.Context baseContext = AgentRuntimeContext.get();
        List<Future<AgentPlanStepExecutionResult>> futures = new ArrayList<>();
        for (AgentPlanStep step : wave) {
            futures.add(planStepPool().submit(() -> {
                if (baseContext != null) {
                    AgentRuntimeContext.set(
                            baseContext.getUserId(),
                            baseContext.getConversationId(),
                            baseContext.getTraceId(),
                            baseContext.getIntentCode(),
                            baseContext.getConfirmedToolNames()
                    );
                }
                try {
                    return runStep(plan, step, params, stepOutputs);
                } finally {
                    AgentRuntimeContext.clear();
                }
            }));
        }

        List<AgentPlanStepExecutionResult> results = new ArrayList<>();
        for (Future<AgentPlanStepExecutionResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BizException("并行执行被中断");
            } catch (ExecutionException exception) {
                throw new BizException("并行执行失败：" + exception.getCause().getMessage());
            }
        }
        return results;
    }

    // 单步执行入口（主线程与工作线程共用）：无工具步骤标记 SKIPPED
    private AgentPlanStepExecutionResult runStep(
            AgentPlan plan,
            AgentPlanStep step,
            Map<String, Object> params,
            Map<Integer, Object> stepOutputs
    ) {
        String toolName = chooseExecutableToolName(plan, step);
        if (!StringUtils.hasText(toolName)) {
            return skipStep(plan, step, "非工具步骤，已由 Planner/Executor 前置校验覆盖。");
        }
        return executeToolStep(plan, step, toolName, params, stepOutputs);
    }

    /*
     * 提取步骤的输入依赖：toolInputSchema 中 {{step_N.output.xxx}} 且 N < 当前 stepNo 的引用。
     * 前向引用（N >= 当前）按占位符 fallback 语义忽略，不构成执行依赖——依赖图因此天然无环。
     */
    private Set<Integer> extractDependencies(AgentPlanStep step) {
        if (step == null || step.getStepNo() == null || !StringUtils.hasText(step.getToolInputSchema())) {
            return Set.of();
        }
        Set<Integer> dependencies = new HashSet<>();
        Matcher matcher = STEP_OUTPUT_PLACEHOLDER.matcher(step.getToolInputSchema());
        while (matcher.find()) {
            try {
                int refStepNo = Integer.parseInt(matcher.group(1));
                if (refStepNo < step.getStepNo()) {
                    dependencies.add(refStepNo);
                }
            } catch (NumberFormatException ignored) {
                // 非数字序号忽略
            }
        }
        return dependencies;
    }

    private boolean depsSatisfied(AgentPlanStep step, Set<Integer> resolvedStepNos) {
        return resolvedStepNos.containsAll(extractDependencies(step));
    }

    /*
     * 是否只读工具：只有 Schema 明确声明 READ_ONLY 才允许并行。
     * Schema 缺失/未声明/工具名解析不了一律保守按写处理（串行屏障）——安全默认项。
     */
    private boolean isReadOnlyTool(AgentPlanStep step) {
        if (step == null || !StringUtils.hasText(step.getToolName())) {
            return false;
        }
        String toolName = step.getToolName().trim();
        Optional<AgentToolSchema> schema = agentToolSchemaRegistry.find(toolName);
        if (schema.isEmpty()) {
            schema = agentToolSchemaRegistry.find(normalizeToolName(toolName));
        }
        return schema.map(s -> AgentToolSideEffectType.READ_ONLY.equals(s.getSideEffectType())).orElse(false);
    }

    // 尽力把已完成步骤的历史摘要（工具输出预览）播种进输出快照，供恢复/并行场景的占位符引用
    private void seedOutputFromSummary(Map<Integer, Object> stepOutputs, AgentPlanStep step) {
        if (step.getStepNo() == null) {
            return;
        }
        Object parsed = readJsonObject(step.getResultSummary());
        if (parsed != null) {
            stepOutputs.put(step.getStepNo(), parsed);
        }
    }

    // 并行波次观测事件：后台可看到“哪些步骤被并行执行”，波次大小即并行度
    private void recordWaveEvent(AgentPlan plan, List<AgentPlanStep> wave) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("waveSize", wave.size());
        requestSnapshot.put("stepNos", wave.stream().map(AgentPlanStep::getStepNo).toList());
        requestSnapshot.put("tools", wave.stream().map(AgentPlanStep::getToolName).toList());

        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(plan.getTraceId())
                .userId(plan.getUserId())
                .conversationId(plan.getConversationId())
                .planId(plan.getId())
                .intentCode(plan.getIntentCode())
                .eventType(AgentObservationEventType.EXECUTOR)
                .eventName("PlanWaveExecute")
                .status(AgentObservationStatus.SUCCESS)
                .requestSnapshot(requestSnapshot)
                .build());
    }

    /*
     * 计划步骤并行线程池（懒加载，守护线程）:
     * 1. 只跑只读步骤（写步骤按选波规则不会进池），从根上避免并行写业务数据；
     * 2. 有界队列 + CallerRunsPolicy：池满时由提交线程（执行主线程）兜底执行，绝不死任务、绝不无限排队；
     * 3. 守护线程不阻止 JVM 退出；@PreDestroy 优雅关闭。
     */
    private volatile ExecutorService planStepPool;

    private ExecutorService planStepPool() {
        ExecutorService pool = planStepPool;
        if (pool == null) {
            synchronized (this) {
                pool = planStepPool;
                if (pool == null) {
                    pool = new ThreadPoolExecutor(
                            4,
                            8,
                            60L,
                            TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(64),
                            new AgentPlanStepThreadFactory(),
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    planStepPool = pool;
                }
            }
        }
        return pool;
    }

    @PreDestroy
    void shutdownPlanStepPool() {
        ExecutorService pool = planStepPool;
        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException exception) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // 计划步骤线程工厂：守护线程 + 可识别名称，便于线程转储排查
    private static final class AgentPlanStepThreadFactory implements ThreadFactory {

        private final AtomicLong sequence = new AtomicLong(1L);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "agent-plan-step-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
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
            Map<String, Object> params,
            Map<Integer, Object> stepOutputs
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
                    buildExecutionParams(params, plan, step, stepOutputs),
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

    private Map<String, Object> buildExecutionParams(
            Map<String, Object> params,
            AgentPlan plan,
            AgentPlanStep step,
            Map<Integer, Object> stepOutputs
    ) {
        // 1. 计划级参数打底
        Map<String, Object> merged = new LinkedHashMap<>();
        if (params != null) {
            merged.putAll(params);
        }
        // 2. 叠加当前步骤自己的可执行参数（LLM 补步会写入；模板步骤等于计划参数，无副作用）
        merged.putAll(readStepExecutableParams(step));
        // 3. 解析 {{step_N.output.xxx}} 占位符，用前序步骤输出做步骤间数据传递
        merged = resolvePlaceholders(merged, stepOutputs);
        // 4. 检索类工具兜底 query/message
        merged.putIfAbsent("message", plan.getUserGoal());
        merged.putIfAbsent("query", plan.getUserGoal());
        return merged;
    }

    // 读取步骤 toolInputSchema 里由 Planner 写入的可执行参数（键 extractedParams）
    private Map<String, Object> readStepExecutableParams(AgentPlanStep step) {
        if (step == null || !StringUtils.hasText(step.getToolInputSchema())) {
            return Map.of();
        }
        try {
            Map<String, Object> schema = objectMapper.readValue(step.getToolInputSchema(), new TypeReference<>() {
            });
            Object extracted = schema.get("extractedParams");
            if (extracted instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
        } catch (Exception exception) {
            log.debug("解析步骤可执行参数失败，stepId={}, error={}", step.getId(), exception.getMessage());
        }
        return Map.of();
    }

    // 执行后把该步工具输出（解析后的 JSON）记入 stepOutputs，供后续步骤占位符引用
    private void rememberStepOutput(Map<Integer, Object> stepOutputs, AgentPlanStepExecutionResult result) {
        if (result == null || result.getStepNo() == null || result.getToolResult() == null) {
            return;
        }
        Object parsed = readJsonObject(result.getToolResult().getDataJson());
        if (parsed != null) {
            stepOutputs.put(result.getStepNo(), parsed);
        }
    }

    // 把参数值里的 {{step_N.output.xxx}} 占位符替换成前序步骤输出
    private Map<String, Object> resolvePlaceholders(Map<String, Object> params, Map<Integer, Object> stepOutputs) {
        if (params == null || params.isEmpty() || stepOutputs == null || stepOutputs.isEmpty()) {
            return params;
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        params.forEach((key, value) -> resolved.put(key, resolveValue(value, stepOutputs)));
        return resolved;
    }

    private Object resolveValue(Object value, Map<Integer, Object> stepOutputs) {
        if (value instanceof String text) {
            Matcher matcher = STEP_OUTPUT_PLACEHOLDER.matcher(text);
            if (!matcher.find()) {
                return text;
            }
            // 整个值就是一个占位符 -> 保留原始类型（如 Long/String/Map）
            if (matcher.start() == 0 && matcher.end() == text.length()) {
                return resolveToken(matcher.group(1), matcher.group(2), stepOutputs, text);
            }
            // 嵌在字符串里 -> 逐个替换为字符串形式
            StringBuilder builder = new StringBuilder();
            matcher.reset();
            while (matcher.find()) {
                Object resolved = resolveToken(matcher.group(1), matcher.group(2), stepOutputs, null);
                String replacement = resolved == null ? matcher.group(0) : String.valueOf(resolved);
                matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(builder);
            return builder.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), resolveValue(v, stepOutputs)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(resolveValue(item, stepOutputs)));
            return copy;
        }
        return value;
    }

    // 解析单个占位符：stepNo 定位前序输出，path 按段取字段（支持 field 与 field[index]）；解析不到返回 fallback（保留原样）
    private Object resolveToken(String stepNoText, String path, Map<Integer, Object> stepOutputs, Object fallback) {
        try {
            Object output = stepOutputs.get(Integer.valueOf(stepNoText));
            if (output == null) {
                return fallback;
            }
            if (path == null || path.isEmpty() || ".".equals(path)) {
                return output;
            }
            // 路径可能以 [0] 开头（输出本身是数组），此时不剥前导点号
            String normalized = path.startsWith(".") ? path.substring(1) : path;
            Object current = output;
            for (String part : normalized.split("\\.")) {
                if (part.isEmpty()) {
                    continue;
                }
                current = descendPath(current, part);
                if (current == null) {
                    return fallback;
                }
            }
            return current;
        } catch (Exception exception) {
            return fallback;
        }
    }

    /*
     * 路径单段下降（任务 J1 数组下标支持）:
     * 1. jobs[0]：先取 Map 字段 jobs，再取 List 下标 0；
     * 2. [0]：当前节点本身是 List，直接取下标；
     * 3. 普通字段：Map 取值；类型不匹配返回 null（调用方回落 fallback）。
     */
    private Object descendPath(Object current, String part) {
        Matcher indexed = INDEXED_FIELD.matcher(part);
        if (indexed.matches()) {
            Object container = current instanceof Map<?, ?> map ? map.get(indexed.group(1)) : null;
            return pickIndex(container, Integer.parseInt(indexed.group(2)));
        }
        Matcher bare = BARE_INDEX.matcher(part);
        if (bare.matches()) {
            return pickIndex(current, Integer.parseInt(bare.group(1)));
        }
        return current instanceof Map<?, ?> map ? map.get(part) : null;
    }

    private Object pickIndex(Object container, int index) {
        if (container instanceof List<?> list && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    // 把工具输出 JSON 解析成对象（Map/List/标量），失败返回 null
    private Object readJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception exception) {
            return null;
        }
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
