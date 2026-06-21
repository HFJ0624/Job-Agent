package com.job.bootstrap.agent.executor;

import com.job.bootstrap.agent.tools.GreetingGenerateTool;
import com.job.bootstrap.agent.tools.InterviewPrepareTool;
import com.job.bootstrap.agent.tools.JobMatchTool;
import com.job.bootstrap.agent.tools.JobRecommendTool;
import com.job.bootstrap.agent.tools.JobSearchTool;
import com.job.bootstrap.agent.tools.MockInterviewReviewTool;
import com.job.bootstrap.agent.tools.RagSearchTool;
import com.job.bootstrap.agent.tools.ResumeAnalyzeTool;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 工具统一调用器
 * 日期:2026/6/20
 */
@Component
@RequiredArgsConstructor
public class AgentToolInvoker {

    private final ResumeAnalyzeTool resumeAnalyzeTool;
    private final JobMatchTool jobMatchTool;
    private final GreetingGenerateTool greetingGenerateTool;
    private final JobSearchTool jobSearchTool;
    private final JobRecommendTool jobRecommendTool;
    private final InterviewPrepareTool interviewPrepareTool;
    private final MockInterviewReviewTool mockInterviewReviewTool;
    private final RagSearchTool ragSearchTool;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final AgentGuardrailService agentGuardrailService;

    /**
     * 执行指定工具。
     *
     * @param toolName 工具唯一名称
     * @param params Planner 抽取出的参数
     * @param originalMessage 用户原始输入
     * @return 工具统一执行结果
     */
    public AgentToolExecutionResult invoke(
            String toolName,
            Map<String, Object> params,
            String originalMessage
    ) {
        long start = System.currentTimeMillis();

        try {
            /*
             * 1. 根据 toolName 做明确分发。
             *    第一版不使用反射，是为了让参数映射更可控，也便于后续逐个工具加业务策略。
             */
            String dataJson = switch (toolName) {
                case "ResumeAnalyzeTool.analyzeResume" -> resumeAnalyzeTool.analyzeResume(
                        getLong(params, "resumeId"),
                        getString(params, "targetPosition")
                );
                case "JobMatchTool.matchJob" -> jobMatchTool.matchJob(
                        getLong(params, "resumeId"),
                        getLong(params, "jobId")
                );
                case "GreetingGenerateTool.generateGreeting" -> greetingGenerateTool.generateGreeting(
                        getLong(params, "resumeId"),
                        getLong(params, "jobId"),
                        getString(params, "style")
                );
                case "JobSearchTool.searchJobs" -> jobSearchTool.searchJobs(
                        getString(params, "keyword"),
                        getString(params, "city"),
                        getString(params, "educationReq"),
                        getString(params, "experienceReq")
                );
                case "JobRecommendTool.recommendJobs" -> jobRecommendTool.recommendJobs(
                        getString(params, "keyword"),
                        getString(params, "city"),
                        getInteger(params, "limit")
                );
                case "InterviewPrepareTool.prepareInterview" -> interviewPrepareTool.prepareInterview(
                        getLong(params, "applicationId"),
                        getLong(params, "resumeId")
                );
                case "MockInterviewReviewTool.generateMockInterviewReview" -> mockInterviewReviewTool.generateMockInterviewReview(
                        firstLong(params, "sessionId", "mockSessionId")
                );
                case "RagSearchTool.searchKnowledge" -> ragSearchTool.searchKnowledge(
                        firstString(params, "query", "message", "keyword", originalMessage),
                        getInteger(params, "limit")
                );
                default -> throw new AgentToolException(
                        AgentToolErrorCode.TOOL_NOT_REGISTERED,
                        toolName,
                        "Executor 不支持调用工具: " + toolName
                );
            };

            /*
             * 2. 工具输出 JSON 校验。
             *    工具类返回 String 是为了兼容 LangChain4j Tool 调用，但 Executor 不能盲信这个 String。
             *    这里统一校验 JSON 语法和关键字段，避免 Summary Assistant 基于非法输出产生幻觉。
             */
            AgentToolSchema schema = agentToolSchemaRegistry.getRequired(toolName);
            agentGuardrailService.validateToolOutput(toolName, schema, dataJson);

            /*
             * 3. 工具执行成功后统一包装结果。
             *    真实业务 Trace 已经由各 Tool 内部记录，这里只返回给 Executor 更新 step。
             */
            return AgentToolExecutionResult.builder()
                    .success(true)
                    .toolName(toolName)
                    .message("工具执行成功")
                    .dataJson(dataJson)
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        } catch (AgentToolException exception) {
            return AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(exception.getToolErrorCode().name())
                    .message(exception.getMessage())
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception exception) {
            return AgentToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .errorCode(AgentToolErrorCode.TOOL_EXECUTION_FAILED.name())
                    .message(exception.getMessage())
                    .costTime(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private Long firstLong(Map<String, Object> params, String firstKey, String secondKey) {
        Long value = getLong(params, firstKey);
        return value != null ? value : getLong(params, secondKey);
    }

    private String firstString(Map<String, Object> params, String firstKey, String secondKey, String thirdKey, String fallback) {
        String value = getString(params, firstKey);
        if (value != null) {
            return value;
        }
        value = getString(params, secondKey);
        if (value != null) {
            return value;
        }
        value = getString(params, thirdKey);
        return value != null ? value : fallback;
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text.trim());
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return null;
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
