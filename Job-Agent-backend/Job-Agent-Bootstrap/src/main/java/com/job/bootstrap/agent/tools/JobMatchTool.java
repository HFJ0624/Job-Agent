package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.agent.tools.resolver.AgentEntityResolveResult;
import com.job.bootstrap.agent.tools.resolver.AgentEntityResolver;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: 岗位匹配工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class JobMatchTool {

    private static final String TOOL_NAME = "JobMatchTool.matchJob";

    private final JobMatchService jobMatchService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;
    private final AgentEntityResolver agentEntityResolver;

    /**
     * 分析当前登录用户某份简历与岗位的匹配度。
     *
     * 兼容说明:
     * 1. 旧入口可以继续传 resumeId/jobId。
     * 2. 新对话入口优先传 resumeName/jobTitle，由工具内部解析真实 ID。
     */
    @Tool("""
            根据用户简历和岗位，分析简历与岗位的匹配度。
            用户可以提供简历名称和岗位名称，例如「黄锋杰(后端)简历」和「Java 后端开发」。
            岗位名称可能命中多个岗位，工具会返回候选列表，让用户确认具体岗位后再执行匹配。
            """)
    public String matchJob(
            @P("简历ID，兼容旧入口；新对话优先使用简历名称") Long resumeId,
            @P("岗位ID，兼容旧入口；新对话优先使用岗位名称") Long jobId
    ) {
        return matchJob(resumeId, null, jobId, null);
    }

    public String matchJob(Long resumeId, String resumeName, Long jobId, String jobTitle) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("resumeId", resumeId);
        input.put("resumeName", resumeName);
        input.put("jobId", jobId);
        input.put("jobTitle", jobTitle);

        AgentToolSchema schema = null;
        try {
            /*
             * 1. 先做统一 Guard 校验。
             * 2. resumeId/jobId 不再由 Schema 强制必填，因为名称也可以解析成 ID。
             */
            schema = agentToolGuard.validate(TOOL_NAME, input);

            JobResume resume = agentEntityResolver.resolveResumeRequired(userId, resumeId, resumeName, TOOL_NAME);
            input.put("resolvedResumeId", resume.getId());
            input.put("resolvedResumeName", resume.getResumeName());

            /*
             * 3. 岗位名称不是唯一的，多命中时不擅自选择，而是返回候选列表让用户确认。
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

            JobMatchVO result = jobMatchService.matchJob(userId, resume.getId(), job.getId());
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
            throw new RuntimeException("岗位匹配工具调用失败: " + exception.getMessage(), exception);
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
