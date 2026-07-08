package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.service.AgentMemoryExtractionService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentMemorySourceType;
import com.job.enums.AgentMemoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Agent 长期记忆提取服务实现，负责从工具执行结果中沉淀结构化长期记忆。
 *
 * <p>核心职责：
 * 在 Agent Executor 完成 Tool Calling 后，遍历执行结果，按 toolName 将简历画像、岗位匹配决策、
 * 面试反馈、沟通话术等关键事实写入记忆库。同时优先沉淀 Planner 解析出的用户偏好
 * （城市、岗位关键词、薪资、目标岗位），保证可解释、低成本、失败面可控。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Memory 子模块（工具结果记忆提取层）。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.chat -> AgentPlanExecutorService.executePlan
 * -> AgentMemoryExtractionService.extractFromExecution
 * -> rememberPlanPreferences（Planner 参数沉淀）
 * -> rememberToolResult -> 按 toolName 分发
 * -> AgentMemoryService.saveOrUpdateMemory（落库）</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>由 AgentPlanExecutorService 在主链路最后阶段调用，发生在 Observation 与状态回写之后；</li>
 *   <li>仅处理执行成功的 Tool 结果，失败结果属于系统运行状态而非用户事实，避免误导后续 Agent；</li>
 *   <li>稳定事实使用固定 memoryKey 覆盖更新（如 preferred_city），业务结果使用业务 ID 生成 key（如 job_decision_{jobId}）；</li>
 *   <li>当前采用规则提取，后续可在本服务内部替换为 LLM Extractor，对 Executor 与 ChatService 接口保持不变。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 第一版先采用规则提取，不调用大模型做“记忆总结”。
 * 2. 这样可以保证写入内容来源清晰、成本低、失败面小。
 * 3. 后续可以在这个服务内部替换为 LLM Extractor，但对 Executor 和 ChatService 的接口保持不变。</p>
 *
 * 作者: hfj
 * 日期: 2026/6/20
 */
@Service
@RequiredArgsConstructor
public class AgentMemoryExtractionServiceImpl implements AgentMemoryExtractionService {

    private static final int MEMORY_VALUE_PREVIEW_LENGTH = 2000;
    private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal NORMAL_CONFIDENCE = new BigDecimal("0.80");
    private static final BigDecimal HIGH_IMPORTANCE = new BigDecimal("0.80");
    private static final BigDecimal NORMAL_IMPORTANCE = new BigDecimal("0.60");

    private final AgentMemoryService agentMemoryService;
    private final ObjectMapper objectMapper;

    /**
     * 从 Agent 计划执行结果中提取长期记忆，覆盖用户偏好沉淀与工具结果分发。
     *
     * <p>核心处理流程：
     * 1. 校验 plan、executionResult、userId 非空，避免无效请求继续；
     * 2. 读取 Planner 抽取出的 extractedParamsJson，优先沉淀用户明确表达的偏好
     *    （城市、岗位关键词、薪资、目标岗位），固定 memoryKey 覆盖更新；
     * 3. 遍历 Executor 每个步骤，跳过未执行或失败的步骤，避免把系统状态当用户事实；
     * 4. 根据 toolName 分发到对应的 remember* 方法，将工具结果沉淀为结构化长期记忆；
     * 5. 稳定事实使用固定 memoryKey 覆盖更新，业务结果使用业务 ID 生成 key 避免相互覆盖。</p>
     *
     * <p>为什么不保存失败步骤：
     * <ul>
     *   <li>失败结果更多是系统运行状态，不一定代表用户事实；</li>
     *   <li>如果把失败原因当长期记忆，后续可能误导 Agent 决策。</li>
     * </ul></p>
     *
     * @param plan             当前 Agent 计划，提供 userId、planId 与 Planner 抽取参数
     * @param executionResult  Executor 执行结果，包含每个步骤的 toolResult
     */
    @Override
    public void extractFromExecution(AgentPlan plan, AgentPlanExecutionResult executionResult) {
        if (plan == null || executionResult == null || plan.getUserId() == null) {
            return;
        }

        Map<String, Object> extractedParams = readJsonMap(plan.getExtractedParamsJson());
        rememberPlanPreferences(plan, extractedParams);

        if (CollectionUtils.isEmpty(executionResult.getSteps())) {
            return;
        }

        for (AgentPlanStepExecutionResult stepResult : executionResult.getSteps()) {
            AgentToolExecutionResult toolResult = stepResult.getToolResult();
            if (toolResult == null || !Boolean.TRUE.equals(toolResult.getSuccess())) {
                continue;
            }
            rememberToolResult(plan, stepResult, toolResult, extractedParams);
        }
    }

    /**
     * 沉淀 Planner 抽取出的用户偏好（城市、岗位关键词、薪资、目标岗位）。
     *
     * <p>说明：用户在本轮目标里明确提到的城市、岗位关键词、薪资，比从工具结果里猜测更可靠，
     * 因此使用固定 memoryKey 覆盖更新，并标记来源为 AGENT_PLAN。</p>
     *
     * @param plan   当前 Agent 计划，提供 userId 与 planId
     * @param params Planner 解析出的结构化参数 Map
     */
    private void rememberPlanPreferences(AgentPlan plan, Map<String, Object> params) {
        Long userId = plan.getUserId();
        Long planId = plan.getId();

        /*
         * 用户在本轮目标里明确提到的城市、岗位关键词、薪资，比从工具结果里猜测更可靠。
         */
        savePreference(userId, "preferred_city", params.get("city"), "用户偏好的求职城市", planId, HIGH_IMPORTANCE);
        savePreference(userId, "target_role", params.get("keyword"), "用户关注的岗位方向", planId, HIGH_IMPORTANCE);
        savePreference(userId, "min_salary", params.get("minSalary"), "用户期望的最低薪资", planId, NORMAL_IMPORTANCE);
        savePreference(userId, "target_position", params.get("targetPosition"), "用户目标岗位", planId, HIGH_IMPORTANCE);
    }

    /**
     * 按 toolName 分发工具结果到对应的记忆沉淀方法。
     *
     * <p>核心处理流程：
     * 1. 校验 toolName 与工具结果摘要非空，避免写入空记忆；
     * 2. 调用 summarizeToolResult 将工具原始 JSON 摘要为可读文本；
     * 3. 按 toolName 走 switch 分发，未识别工具显式忽略，避免 RAG 临时检索结果被误存为长期事实。</p>
     *
     * @param plan       当前 Agent 计划，提供 userId
     * @param stepResult 当前步骤执行结果，提供 stepId
     * @param toolResult 工具执行结果，提供 toolName 与 dataJson
     * @param params     Planner 抽取出的结构化参数，用于生成业务 ID 后缀
     */
    private void rememberToolResult(
            AgentPlan plan,
            AgentPlanStepExecutionResult stepResult,
            AgentToolExecutionResult toolResult,
            Map<String, Object> params
    ) {
        String toolName = toolResult.getToolName();
        if (!StringUtils.hasText(toolName)) {
            return;
        }

        String value = summarizeToolResult(toolResult.getDataJson());
        if (!StringUtils.hasText(value)) {
            return;
        }

        Long userId = plan.getUserId();
        Long stepId = stepResult.getStepId();

        switch (toolName) {
            case "ResumeAnalyzeTool.analyzeResume" -> rememberResumeAnalysis(userId, stepId, params, value);
            case "JobMatchTool.matchJob" -> rememberJobDecision(userId, stepId, params, value);
            case "InterviewPrepareTool.prepareInterview" -> rememberInterviewPrepare(userId, stepId, params, value);
            case "MockInterviewReviewTool.generateMockInterviewReview" -> rememberMockInterviewReview(userId, stepId, params, value);
            case "GreetingGenerateTool.generateGreeting" -> rememberCommunicationStyle(userId, stepId, params, value);
            case "JobSearchTool.searchJobs", "JobRecommendTool.recommendJobs" -> rememberSearchResult(userId, stepId, params, value);
            default -> {
                /*
                 * 其他工具暂不沉淀长期记忆。
                 * 这里显式忽略，是为了避免把 RAG 临时检索结果误存成用户长期事实。
                 */
            }
        }
    }

    /**
     * 沉淀简历分析结果，包含简历画像摘要与能力短板两条记忆。
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID，作为业务 ID 缺失时的 key 后缀
     * @param params Planner 抽取参数，优先用 resumeId 作为 key 后缀
     * @param value  简历分析工具的摘要结果
     */
    private void rememberResumeAnalysis(Long userId, Long stepId, Map<String, Object> params, String value) {
        String resumeId = toText(params.get("resumeId"));
        String keySuffix = StringUtils.hasText(resumeId) ? resumeId : String.valueOf(stepId);

        saveMemory(
                userId,
                AgentMemoryType.RESUME_PROFILE,
                "resume_profile_" + keySuffix,
                value,
                "简历画像摘要: " + preview(value, 160),
                stepId,
                HIGH_IMPORTANCE
        );

        saveMemory(
                userId,
                AgentMemoryType.SKILL_GAP,
                "resume_skill_gap_" + keySuffix,
                value,
                "简历能力短板线索: " + preview(value, 160),
                stepId,
                NORMAL_IMPORTANCE
        );
    }

    /**
     * 沉淀岗位匹配决策，使用 jobId 作为 key 后缀避免不同岗位相互覆盖。
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID，作为 jobId 缺失时的 key 后缀
     * @param params Planner 抽取参数，优先用 jobId 作为 key 后缀
     * @param value  岗位匹配工具的摘要结果
     */
    private void rememberJobDecision(Long userId, Long stepId, Map<String, Object> params, String value) {
        String jobId = toText(params.get("jobId"));
        String keySuffix = StringUtils.hasText(jobId) ? jobId : String.valueOf(stepId);

        saveMemory(
                userId,
                AgentMemoryType.JOB_DECISION,
                "job_decision_" + keySuffix,
                value,
                "岗位匹配决策: " + preview(value, 160),
                stepId,
                HIGH_IMPORTANCE
        );
    }

    /**
     * 沉淀面试准备重点，使用 applicationId 作为 key 后缀。
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID，作为 applicationId 缺失时的 key 后缀
     * @param params Planner 抽取参数，优先用 applicationId 作为 key 后缀
     * @param value  面试准备工具的摘要结果
     */
    private void rememberInterviewPrepare(Long userId, Long stepId, Map<String, Object> params, String value) {
        String applicationId = toText(params.get("applicationId"));
        String keySuffix = StringUtils.hasText(applicationId) ? applicationId : String.valueOf(stepId);

        saveMemory(
                userId,
                AgentMemoryType.INTERVIEW_FEEDBACK,
                "interview_prepare_" + keySuffix,
                value,
                "面试准备重点: " + preview(value, 160),
                stepId,
                NORMAL_IMPORTANCE
        );
    }

    /**
     * 沉淀模拟面试反馈，包含面试反馈与能力短板两条记忆，使用 mockSessionId 作为 key 后缀。
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID，作为 sessionId 缺失时的 key 后缀
     * @param params Planner 抽取参数，优先用 mockSessionId/sessionId 作为 key 后缀
     * @param value  模拟面试复盘工具的摘要结果
     */
    private void rememberMockInterviewReview(Long userId, Long stepId, Map<String, Object> params, String value) {
        String sessionId = toText(firstValue(params, "mockSessionId", "sessionId"));
        String keySuffix = StringUtils.hasText(sessionId) ? sessionId : String.valueOf(stepId);

        saveMemory(
                userId,
                AgentMemoryType.INTERVIEW_FEEDBACK,
                "mock_interview_feedback_" + keySuffix,
                value,
                "模拟面试反馈: " + preview(value, 160),
                stepId,
                HIGH_IMPORTANCE
        );

        saveMemory(
                userId,
                AgentMemoryType.SKILL_GAP,
                "mock_interview_skill_gap_" + keySuffix,
                value,
                "模拟面试能力短板: " + preview(value, 160),
                stepId,
                NORMAL_IMPORTANCE
        );
    }

    /**
     * 沉淀用户偏好的 HR 沟通风格与最近生成的开场白。
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID
     * @param params Planner 抽取参数，style 字段表示用户偏好的开场白风格
     * @param value  开场白生成工具的摘要结果
     */
    private void rememberCommunicationStyle(Long userId, Long stepId, Map<String, Object> params, String value) {
        Object style = params.get("style");
        if (style != null && StringUtils.hasText(String.valueOf(style))) {
            saveMemory(
                    userId,
                    AgentMemoryType.COMMUNICATION_STYLE,
                    "preferred_greeting_style",
                    String.valueOf(style).trim(),
                    "用户偏好的 HR 开场白风格: " + String.valueOf(style).trim(),
                    stepId,
                    NORMAL_IMPORTANCE
            );
        }

        saveMemory(
                userId,
                AgentMemoryType.COMMUNICATION_STYLE,
                "last_generated_greeting",
                value,
                "最近生成的 HR 沟通话术: " + preview(value, 160),
                stepId,
                NORMAL_IMPORTANCE
        );
    }

    /**
     * 沉淀用户最近搜索的岗位方向，作为弱记忆补充。
     *
     * <p>说明：搜索结果本身不一定是用户偏好，所以这里只补充“本次搜索条件产生过结果”的弱记忆。
     * 明确偏好仍然来自 Planner 参数里的 city、keyword、minSalary。</p>
     *
     * @param userId 当前用户 ID
     * @param stepId 当前步骤 ID
     * @param params Planner 抽取参数，keyword 字段表示本次搜索的岗位关键词
     * @param value  搜索工具的摘要结果
     */
    private void rememberSearchResult(Long userId, Long stepId, Map<String, Object> params, String value) {
        /*
         * 搜索结果本身不一定是用户偏好，所以这里只补充“本次搜索条件产生过结果”的弱记忆。
         * 明确偏好仍然来自 Planner 参数里的 city、keyword、minSalary。
         */
        Object keyword = params.get("keyword");
        if (keyword == null || !StringUtils.hasText(String.valueOf(keyword))) {
            return;
        }

        saveMemory(
                userId,
                AgentMemoryType.CAREER_GOAL,
                "recent_search_goal",
                String.valueOf(keyword).trim(),
                "用户最近搜索的岗位方向: " + String.valueOf(keyword).trim(),
                stepId,
                NORMAL_IMPORTANCE
        );
    }

    /**
     * 保存用户偏好类记忆，来源标记为 AGENT_PLAN，置信度统一为 HIGH_CONFIDENCE。
     *
     * @param userId        当前用户 ID
     * @param memoryKey     固定 memoryKey，例如 preferred_city、target_role
     * @param rawValue      原始参数值，为空时跳过
     * @param summaryPrefix 摘要前缀，用于在记忆库中描述该条事实
     * @param sourceId      来源 ID，对应 planId
     * @param importance    重要性评分，HIGH_IMPORTANCE 或 NORMAL_IMPORTANCE
     */
    private void savePreference(
            Long userId,
            String memoryKey,
            Object rawValue,
            String summaryPrefix,
            Long sourceId,
            BigDecimal importance
    ) {
        if (rawValue == null || !StringUtils.hasText(String.valueOf(rawValue))) {
            return;
        }

        String value = String.valueOf(rawValue).trim();
        agentMemoryService.saveOrUpdateMemory(
                userId,
                AgentMemoryType.USER_PREFERENCE,
                memoryKey,
                value,
                summaryPrefix + ": " + value,
                AgentMemorySourceType.AGENT_PLAN.name(),
                sourceId,
                HIGH_CONFIDENCE,
                importance
        );
    }

    /**
     * 保存工具结果类记忆，来源标记为 TOOL_RESULT，置信度统一为 NORMAL_CONFIDENCE。
     *
     * @param userId      当前用户 ID
     * @param memoryType  记忆类型，例如 RESUME_PROFILE、JOB_DECISION
     * @param memoryKey   记忆唯一 key，稳定事实使用固定 key，业务结果使用业务 ID 生成 key
     * @param memoryValue 记忆值，会被截断到 MEMORY_VALUE_PREVIEW_LENGTH
     * @param summary     摘要文本，用于画像重建时展示
     * @param sourceId    来源 ID，对应 stepId
     * @param importance  重要性评分，HIGH_IMPORTANCE 或 NORMAL_IMPORTANCE
     */
    private void saveMemory(
            Long userId,
            AgentMemoryType memoryType,
            String memoryKey,
            String memoryValue,
            String summary,
            Long sourceId,
            BigDecimal importance
    ) {
        agentMemoryService.saveOrUpdateMemory(
                userId,
                memoryType,
                memoryKey,
                preview(memoryValue, MEMORY_VALUE_PREVIEW_LENGTH),
                summary,
                AgentMemorySourceType.TOOL_RESULT.name(),
                sourceId,
                NORMAL_CONFIDENCE,
                importance
        );
    }

    /**
     * 将工具原始 JSON 摘要为可读文本，支持 Map/List/其他类型。
     *
     * <p>第一版不理解每个工具的完整 VO，只做安全摘要：
     * <ul>
     *   <li>JSON Map：抽取常见关键字段（评分、摘要、建议等），抽不到时保留 JSON 预览；</li>
     *   <li>JSON List：记录条数和第一条预览；</li>
     *   <li>非 JSON：直接截断保存。</li>
     * </ul></p>
     *
     * @param dataJson 工具原始 JSON 字符串
     * @return 摘要文本，解析失败时退化为截断原始 JSON
     */
    private String summarizeToolResult(String dataJson) {
        if (!StringUtils.hasText(dataJson)) {
            return "";
        }

        /*
         * 第一版不理解每个工具的完整 VO，只做安全摘要:
         * - JSON Map: 抽取常见关键字段，抽不到时保留 JSON 预览。
         * - JSON List: 记录条数和第一条预览。
         * - 非 JSON: 直接截断保存。
         */
        try {
            Object value = objectMapper.readValue(dataJson, Object.class);
            if (value instanceof Map<?, ?> map) {
                return summarizeMap(map, dataJson);
            }
            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    return "工具返回空列表";
                }
                return "工具返回 " + list.size() + " 条结果，首条: " + preview(toJson(list.get(0)), 600);
            }
            return preview(toJson(value), MEMORY_VALUE_PREVIEW_LENGTH);
        } catch (Exception exception) {
            return preview(dataJson, MEMORY_VALUE_PREVIEW_LENGTH);
        }
    }

    /**
     * 抽取 Map 中常见关键字段（评分、摘要、建议等）拼接为摘要文本。
     *
     * @param map          工具结果 Map
     * @param originalJson 原始 JSON，关键字段全部缺失时退化为截断原始 JSON
     * @return 摘要文本，受 MEMORY_VALUE_PREVIEW_LENGTH 限制
     */
    private String summarizeMap(Map<?, ?> map, String originalJson) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, map, "score", "评分");
        appendIfPresent(builder, map, "totalScore", "总分");
        appendIfPresent(builder, map, "matchScore", "匹配分");
        appendIfPresent(builder, map, "matchLevel", "匹配等级");
        appendIfPresent(builder, map, "recommendation", "建议");
        appendIfPresent(builder, map, "summary", "摘要");
        appendIfPresent(builder, map, "advantages", "优势");
        appendIfPresent(builder, map, "weaknesses", "不足");
        appendIfPresent(builder, map, "suggestions", "建议列表");
        appendIfPresent(builder, map, "improvementPlan", "提升计划");

        if (builder.isEmpty()) {
            return preview(originalJson, MEMORY_VALUE_PREVIEW_LENGTH);
        }
        return preview(builder.toString(), MEMORY_VALUE_PREVIEW_LENGTH);
    }

    private void appendIfPresent(StringBuilder builder, Map<?, ?> map, String key, String label) {
        Object value = map.get(key);
        if (value == null) {
            return;
        }
        String text = value instanceof String stringValue ? stringValue : toJson(value);
        if (!StringUtils.hasText(text)) {
            return;
        }

        builder.append(label)
                .append(": ")
                .append(preview(text, 500))
                .append('\n');
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

    private Object firstValue(Map<String, Object> params, String firstKey, String secondKey) {
        Object value = params.get(firstKey);
        return value != null ? value : params.get(secondKey);
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private String preview(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
