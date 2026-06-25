package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.agent.tools.resolver.AgentEntityResolver;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.resume.ResumeScoreVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: 简历分析工具
 * 日期: 2026/6/8 15:12
 */
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeTool {

    private static final String TOOL_NAME = "ResumeAnalyzeTool.analyzeResume";

    private final JobResumeScoreService jobResumeScoreService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;
    private final AgentEntityResolver agentEntityResolver;

    /**
     * 分析简历。
     *
     * 兼容说明:
     * 1. LangChain4j 直接调用时仍然支持旧的 resumeId 参数。
     * 2. Executor 调用时会走下面的重载方法，优先传 resumeName。
     */
    @Tool("""
            对当前登录用户的指定简历进行 AI 简历质量评分。
            用户可以提供简历名称，例如「黄锋杰(后端)简历」；也兼容旧的 resumeId。
            如果用户没有提供简历名称或 resumeId，系统会尝试使用当前用户的默认简历。
            targetPosition 可以为空，只表示用户的求职方向，不表示具体 JD。
            """)
    public String analyzeResume(
            @P("简历ID，兼容旧入口；新对话优先使用简历名称") Long resumeId,
            @P("求职方向，可以为空，例如 Java 后端开发、AI Agent 开发") String targetPosition
    ) {
        return analyzeResume(resumeId, null, targetPosition);
    }

    public String analyzeResume(Long resumeId, String resumeName, String targetPosition) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("resumeId", resumeId);
        input.put("resumeName", resumeName);
        input.put("targetPosition", targetPosition);

        AgentToolSchema schema = null;
        try {
            /*
             * 1. 先做统一工具入参、权限、确认策略校验。
             * 2. Schema 层不强制 resumeId，因为新入口允许用户只说简历名称。
             */
            schema = agentToolGuard.validate(TOOL_NAME, input);

            /*
             * 3. 将用户自然语言里的简历名称解析成业务 Service 需要的 resumeId。
             * 4. 如果用户没有提供名称或 ID，则尝试使用默认简历。
             */
            JobResume resume = agentEntityResolver.resolveResumeRequired(userId, resumeId, resumeName, TOOL_NAME);
            input.put("resolvedResumeId", resume.getId());
            input.put("resolvedResumeName", resume.getResumeName());
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            ResumeScoreVO result = jobResumeScoreService.scoreResume(userId, resume.getId(), targetPosition);
            String json = objectMapper.writeValueAsString(result);

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

            return json;
        } catch (Exception exception) {
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    agentToolGuard.buildTraceInput(TOOL_NAME, schema, input),
                    null,
                    "FAILED",
                    exception.getMessage(),
                    System.currentTimeMillis() - start
            );

            if (exception instanceof AgentToolException toolException) {
                throw toolException;
            }
            throw new RuntimeException("简历分析失败: " + exception.getMessage(), exception);
        }
    }
}
