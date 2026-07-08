package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.plan.AgentPlanStepTemplate;
import com.job.bootstrap.agent.plan.AgentPlanTemplate;
import com.job.bootstrap.agent.plan.AgentPlanTemplateFactory;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.service.AgentPlanningService;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.common.vo.agent.AgentPlanStepVO;
import com.job.common.vo.agent.AgentPlanVO;
import com.job.enums.AgentPlanStatus;
import com.job.enums.AgentPlanStepStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 计划生成服务实现，负责把用户目标拆成可执行的 Plan。
 *
 * <p>核心职责：
 * 1. 根据用户目标、意图和长期记忆上下文抽取关键参数。
 * 2. 根据意图选择计划模板，生成计划标题、摘要、步骤。
 * 3. 检查必填参数是否齐全，缺失则标记为 NEED_CLARIFICATION。
 * 4. 把计划和步骤落库，供 Executor 确定性执行。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Service 层（Planner 阶段）。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.chat -> AgentPlanningServiceImpl.createPlan
 * -> AgentPlanParameterExtractor (抽取参数)
 * -> AgentPlanTemplateFactory (选择模板)
 * -> AgentPlanMapper / AgentPlanStepMapper (落库)
 * -> 返回 AgentPlanVO 交给 Executor 执行</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>AgentPlanParameterExtractor 负责从自然语言 + 记忆上下文抽取 resumeId、jobId、city 等参数；</li>
 *   <li>AgentPlanTemplateFactory 按意图返回固定步骤模板，避免模型自由发挥导致步骤不可控；</li>
 *   <li>计划落库后由 AgentPlanExecutorService 按 stepNo 顺序执行；</li>
 *   <li>hasRequiredParam 中允许 resumeName/jobTitle 作为 resumeId/jobId 的自然语言替代，真正解析留给 Tool。</li>
 * </ul></p>
 *
 * <p>Agent 阶段说明：本服务对应 Planning 阶段，输入是用户目标 + 意图 + 记忆上下文，
 * 输出是结构化 Plan（含步骤、工具建议、完成条件），不调用任何工具。</p>
 *
 * 作者:hfj
 * 日期:2026/6/19
 */
@Service
@RequiredArgsConstructor
public class AgentPlanningServiceImpl implements AgentPlanningService {

    private static final int NOT_DELETED = 0;

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final AgentPlanTemplateFactory templateFactory;
    private final ObjectMapper objectMapper;

    /**
     * 根据用户目标生成计划并落库。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanVO createPlan(
            Long userId,
            Long conversationId,
            String traceId,
            AgentIntentCode intentCode,
            String userGoal
    ) {
        return createPlan(userId, conversationId, traceId, intentCode, userGoal, null);
    }

    /**
     * 根据用户目标和长期记忆上下文生成计划。
     *
     * 方法步骤:
     * 1. userGoal 仍然作为计划原始目标落库，保持后台看到的是用户真实输入。
     * 2. 参数抽取时额外拼接 planningContext，让 preferred_city、target_role 等长期记忆可以补充本轮缺省条件。
     * 3. 计划模板仍然根据用户原始目标选择，避免内部记忆把用户本轮意图带偏。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPlanVO createPlan(
            Long userId,
            Long conversationId,
            String traceId,
            AgentIntentCode intentCode,
            String userGoal,
            String planningContext
    ) {
        String extractionText = buildExtractionText(userGoal, planningContext);
        Map<String, Object> extractedParams = parameterExtractor.extract(extractionText);
        AgentPlanTemplate template = templateFactory.create(intentCode, userGoal);
        List<String> missingParams = findMissingParams(template.requiredParams(), extractedParams);
        String status = CollectionUtils.isEmpty(missingParams)
                ? AgentPlanStatus.PLANNED.name()
                : AgentPlanStatus.NEED_CLARIFICATION.name();

        Date now = new Date();
        AgentPlan plan = new AgentPlan();
        plan.setTraceId(traceId);
        plan.setUserId(userId);
        plan.setConversationId(conversationId);
        plan.setIntentCode(intentCode.name());
        plan.setUserGoal(userGoal);
        plan.setPlanTitle(template.planTitle());
        plan.setPlanSummary(template.planSummary());
        plan.setRequiredParamsJson(toJson(template.requiredParams()));
        plan.setExtractedParamsJson(toJson(extractedParams));
        plan.setMissingParamsJson(toJson(missingParams));
        plan.setStatus(status);
        plan.setIsDeleted(NOT_DELETED);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        agentPlanMapper.insert(plan);

        int stepNo = 1;
        for (AgentPlanStepTemplate stepTemplate : template.steps()) {
            AgentPlanStep step = new AgentPlanStep();
            step.setPlanId(plan.getId());
            step.setUserId(userId);
            step.setConversationId(conversationId);
            step.setStepNo(stepNo++);
            step.setStepName(stepTemplate.stepName());
            step.setStepGoal(stepTemplate.stepGoal());
            step.setToolName(stepTemplate.toolName());
            step.setToolInputSchema(toJson(buildToolInputSchema(stepTemplate, extractedParams)));
            step.setCompletionCriteria(stepTemplate.completionCriteria());
            step.setStatus(AgentPlanStepStatus.PENDING.name());
            step.setIsDeleted(NOT_DELETED);
            step.setCreateTime(now);
            step.setUpdateTime(now);
            agentPlanStepMapper.insert(step);
        }

        return buildPlanVO(plan);
    }

    private String buildExtractionText(String userGoal, String planningContext) {
        if (!StringUtils.hasText(planningContext)) {
            return userGoal;
        }
        return userGoal + "\n\n【可用于参数抽取的长期记忆上下文】\n" + planningContext;
    }

    /**
     * 查询当前用户的一份 Agent 计划。
     *
     * 方法步骤:
     * 1. 根据 planId 查询计划实体。
     * 2. 校验计划存在、未删除、属于当前登录用户。
     * 3. 查询计划步骤并转换成 VO。
     */
    @Override
    public AgentPlanVO getUserPlan(Long userId, Long planId) {
        AgentPlan plan = agentPlanMapper.selectById(planId);
        if (plan == null || Integer.valueOf(1).equals(plan.getIsDeleted())) {
            throw new BizException("Agent 计划不存在");
        }
        if (!userId.equals(plan.getUserId())) {
            throw new BizException("Agent 计划不存在或无权限访问");
        }
        return buildPlanVO(plan);
    }

    private List<String> findMissingParams(List<String> requiredParams, Map<String, Object> extractedParams) {
        if (CollectionUtils.isEmpty(requiredParams)) {
            return List.of();
        }

        return requiredParams.stream()
                .filter(param -> !hasRequiredParam(param, extractedParams))
                .toList();
    }

    private boolean hasRequiredParam(String param, Map<String, Object> extractedParams) {
        if (extractedParams.containsKey(param)) {
            return true;
        }
        /*
         * 用户端对话不应该强迫用户说数据库 ID。
         * Planner 层把 resumeName/jobTitle 视为 resumeId/jobId 的自然语言替代项，真正解析留给 Tool。
         */
        if ("resumeId".equals(param)) {
            return extractedParams.containsKey("resumeName");
        }
        if ("jobId".equals(param)) {
            return extractedParams.containsKey("jobTitle");
        }
        return false;
    }

    private Map<String, Object> buildToolInputSchema(
            AgentPlanStepTemplate stepTemplate,
            Map<String, Object> extractedParams
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("expectedInput", stepTemplate.toolInputSchema());
        schema.put("extractedParams", extractedParams);
        schema.put("note", "Planner 生成工具约束，Executor 按计划步骤确定性调用工具。");
        return schema;
    }

    private AgentPlanVO buildPlanVO(AgentPlan plan) {
        List<AgentPlanStep> steps = agentPlanStepMapper.selectList(
                new LambdaQueryWrapper<AgentPlanStep>()
                        .eq(AgentPlanStep::getPlanId, plan.getId())
                        .eq(AgentPlanStep::getIsDeleted, NOT_DELETED)
                        .orderByAsc(AgentPlanStep::getStepNo)
        );

        AgentPlanVO vo = AgentPlanVO.from(plan);
        vo.setSteps(steps.stream().map(AgentPlanStepVO::from).toList());
        return vo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
