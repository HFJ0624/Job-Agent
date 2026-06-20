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
 * 作者:hfj
 * 功能:Agent 长期记忆提取服务实现
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 第一版先采用规则提取，不调用大模型做“记忆总结”。
 * 2. 这样可以保证写入内容来源清晰、成本低、失败面小。
 * 3. 后续可以在这个服务内部替换为 LLM Extractor，但对 Executor 和 ChatService 的接口保持不变。
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
     * 从计划执行结果中提取长期记忆。
     *
     * 方法步骤:
     * 1. 读取 Planner 抽取出的参数，先沉淀明确的用户偏好，例如城市、岗位关键词、薪资。
     * 2. 遍历 Executor 每个步骤，只处理执行成功且带有工具结果的步骤。
     * 3. 根据 toolName 判断这类结果应该沉淀成哪一种记忆。
     * 4. 对同一类稳定事实使用固定 memoryKey 覆盖更新，对业务结果使用业务 ID 生成 key。
     *
     * 为什么不保存失败步骤:
     * - 失败结果更多是系统运行状态，不一定代表用户事实。
     * - 如果把失败原因当长期记忆，后续可能误导 Agent。
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
