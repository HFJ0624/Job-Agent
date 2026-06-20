package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.service.AgentMemoryExtractionService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentPlanStatus;
import com.job.enums.AgentPlanStepStatus;
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

/**
 * 作者:hfj
 * 功能:Agent 计划执行服务实现
 * 日期:2026/6/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPlanExecutorServiceImpl implements AgentPlanExecutorService {

    private static final int NOT_DELETED = 0;
    private static final int RESULT_PREVIEW_LENGTH = 1000;

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;
    private final AgentToolInvoker agentToolInvoker;
    private final AgentMemoryExtractionService agentMemoryExtractionService;
    private final ObjectMapper objectMapper;

    /**
     * 执行指定计划。
     *
     * 方法步骤:
     * 1. 校验计划存在、属于当前用户，并且没有被逻辑删除。
     * 2. 读取计划步骤，按 stepNo 升序执行。
     * 3. 对没有 toolName 的步骤标记 SKIPPED，表示这是规划/校验类步骤。
     * 4. 对有 toolName 的步骤设置 RUNNING，然后调用 AgentToolInvoker。
     * 5. 工具成功则步骤 COMPLETED；工具失败则步骤 FAILED，并终止后续步骤。
     * 6. 根据所有步骤结果更新计划状态为 COMPLETED 或 FAILED。
     * 7. 返回结构化执行结果，供 Summary Assistant 生成中文回复。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanExecutionResult executePlan(Long userId, Long planId) {
        AgentPlan plan = loadUserPlan(userId, planId);
        List<AgentPlanStep> steps = loadPlanSteps(planId);

        /*
         * 幂等保护:
         * 已完成或已失败的计划不再重复执行，避免重复生成评分、匹配、沟通记录等业务数据。
         */
        if (AgentPlanStatus.COMPLETED.name().equals(plan.getStatus())) {
            return buildExistingPlanResult(plan, steps, true, "计划已执行完成，无需重复执行");
        }
        if (AgentPlanStatus.FAILED.name().equals(plan.getStatus())) {
            return buildExistingPlanResult(plan, steps, false, "计划已执行失败，请重新发起任务");
        }

        Map<String, Object> params = readJsonMap(plan.getExtractedParamsJson());

        List<AgentPlanStepExecutionResult> stepResults = new ArrayList<>();
        boolean failed = false;
        String failReason = null;

        for (AgentPlanStep step : steps) {
            if (failed) {
                stepResults.add(skipStep(step, "前置步骤失败，跳过执行。"));
                continue;
            }

            String toolName = chooseExecutableToolName(plan, step);
            if (!StringUtils.hasText(toolName)) {
                stepResults.add(skipStep(step, "非工具步骤，已由 Planner/Executor 前置校验覆盖。"));
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

    private AgentPlanStepExecutionResult skipStep(AgentPlanStep step, String reason) {
        step.setStatus(AgentPlanStepStatus.SKIPPED.name());
        step.setResultSummary(reason);
        step.setErrorMsg(null);
        step.setUpdateTime(new Date());
        agentPlanStepMapper.updateById(step);

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
             * 3. 调用统一工具调用器。
             *    Invoker 内部会复用现有 Tool，因此原有业务 Service、Tool Guard、Tool Trace 都不会绕过。
             */
            AgentToolExecutionResult toolResult = agentToolInvoker.invoke(
                    toolName,
                    buildExecutionParams(params, plan),
                    plan.getUserGoal()
            );

            if (Boolean.TRUE.equals(toolResult.getSuccess())) {
                return markStepCompleted(step, toolName, toolResult);
            }
            return markStepFailed(step, toolName, toolResult);
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
    }

    private AgentPlanStepExecutionResult markStepCompleted(
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
            return toolName.trim();
        }

        /*
         * 第一版只处理岗位搜索/推荐的二选一。
         * 用户明确说“推荐/适合/偏好”时使用推荐工具，否则使用普通搜索工具。
         */
        String goal = plan.getUserGoal() == null ? "" : plan.getUserGoal();
        if (goal.contains("推荐") || goal.contains("适合") || goal.contains("偏好")) {
            return "JobRecommendTool.recommendJobs";
        }
        return "JobSearchTool.searchJobs";
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
