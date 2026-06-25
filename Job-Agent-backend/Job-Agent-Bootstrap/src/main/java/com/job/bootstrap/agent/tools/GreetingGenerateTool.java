package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.agent.tools.resolver.AgentEntityResolveResult;
import com.job.bootstrap.agent.tools.resolver.AgentEntityResolver;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobGreetingService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.greeting.GreetingVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: HR 打招呼语生成工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class GreetingGenerateTool {

    private static final String TOOL_NAME = "GreetingGenerateTool.generateGreeting";
    private static final String DEFAULT_STYLE = "礼貌";

    private final JobGreetingService jobGreetingService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;
    private final AgentEntityResolver agentEntityResolver;

    /**
     * 生成 HR 打招呼语。
     *
     * 兼容说明:
     * 1. 旧入口可以继续传 resumeId/jobId。
     * 2. 新对话入口优先传 resumeName/jobTitle。
     */
    @Tool("""
            根据用户简历和岗位生成 HR 打招呼语。
            用户可以提供简历名称和岗位名称，例如「黄锋杰(后端)简历」和「Java 后端开发」。
            如果岗位名称命中多个岗位，工具会返回候选列表，让用户确认具体岗位。
            """)
    public String generateGreeting(
            @P("简历ID，兼容旧入口；新对话优先使用简历名称") Long resumeId,
            @P("岗位ID，兼容旧入口；新对话优先使用岗位名称") Long jobId,
            @P("语气风格，例如 礼貌、简洁、积极、正式，可以为空") String style
    ) {
        return generateGreeting(resumeId, null, jobId, null, style);
    }

    public String generateGreeting(Long resumeId, String resumeName, Long jobId, String jobTitle, String style) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();
        String finalStyle = style == null ? DEFAULT_STYLE : style;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("resumeId", resumeId);
        input.put("resumeName", resumeName);
        input.put("jobId", jobId);
        input.put("jobTitle", jobTitle);
        input.put("style", finalStyle);

        AgentToolSchema schema = null;
        try {
            /*
             * 1. 先做统一 Guard 校验。
             * 2. 本工具有副作用，仍然复用原来的用户确认机制。
             */
            schema = agentToolGuard.validate(TOOL_NAME, input);

            JobResume resume = agentEntityResolver.resolveResumeRequired(userId, resumeId, resumeName, TOOL_NAME);
            input.put("resolvedResumeId", resume.getId());
            input.put("resolvedResumeName", resume.getResumeName());

            /*
             * 3. 岗位名称可能对应多个公司/城市的同名岗位，因此多命中时先让用户选。
             */
            AgentEntityResolveResult jobResolve = agentEntityResolver.resolveJob(jobId, jobTitle, TOOL_NAME);
            if (jobResolve.isNeedClarification()) {
                Map<String, Object> clarification = buildClarificationResult(jobResolve);
                saveTrace(userId, conversationId, intentCode, input, schema, clarification, "SUCCESS", null, start);
                return objectMapper.writeValueAsString(clarification);
            }

            JobPosition job = jobResolve.getJob();
            input.put("resolvedJobId", job.getId());
            input.put("resolvedJobTitle", job.getJobTitle());
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            GreetingVO result = jobGreetingService.generateGreeting(
                    userId,
                    resume.getId(),
                    job.getId(),
                    finalStyle
            );

            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    traceInput,
                    result,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            saveTrace(userId, conversationId, intentCode, input, schema, null, "FAILED", exception.getMessage(), start);
            if (exception instanceof AgentToolException toolException) {
                throw toolException;
            }
            throw new RuntimeException("打招呼语生成工具调用失败: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildClarificationResult(AgentEntityResolveResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("needClarification", true);
        map.put("message", result.getMessage());
        map.put("candidates", result.getCandidates());
        return map;
    }

    private void saveTrace(
            Long userId,
            Long conversationId,
            String intentCode,
            Map<String, Object> input,
            AgentToolSchema schema,
            Object output,
            String status,
            String errorMessage,
            long start
    ) {
        agentTraceService.saveToolTrace(
                userId,
                conversationId,
                intentCode,
                TOOL_NAME,
                agentToolGuard.buildTraceInput(TOOL_NAME, schema, input),
                output,
                status,
                errorMessage,
                System.currentTimeMillis() - start
        );
    }
}
