package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.interview.InterviewPrepareVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:AI 面试准备工具
 * 使用场景:
 * 1. 用户说“帮我准备这个岗位的面试”
 * 2. 用户说“根据这条投递记录生成面试题”
 * 3. 用户说“帮我准备 applicationId=1 的面试”
 */
@Component
@RequiredArgsConstructor
public class InterviewPrepareTool {

    private static final String TOOL_NAME = "InterviewPrepareTool.prepareInterview";

    private final InterviewPrepareService interviewPrepareService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;

    /**
     * 生成面试准备内容。
     *
     * @param applicationId 投递记录ID
     * @param resumeId 简历ID，可为空
     * @return 面试准备结果 JSON
     */
    @Tool("""
            根据求职投递记录生成面试准备内容。
            当用户要求“准备面试”“生成面试题”“面试复习资料”时使用本工具。
            applicationId 必须由用户输入或前端上下文提供，不能编造。
            """)
    public String prepareInterview(
            @P("求职投递记录ID，必须由用户输入或前端上下文提供，不能编造") Long applicationId,
            @P("简历ID，可为空；如果用户没有提供，可以传 null") Long resumeId
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("applicationId", applicationId);
        input.put("resumeId", resumeId);

        AgentToolSchema schema = null;
        try {
            schema = agentToolGuard.validate(TOOL_NAME, input);
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            InterviewPrepareVO result = interviewPrepareService.generatePrepare(
                    userId,
                    applicationId,
                    resumeId
            );

            /*
             * 工具调用成功，记录 Trace。
             */
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
        } catch (Exception e) {
            /*
             * 工具调用失败，也记录 Trace，便于后台排查。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    agentToolGuard.buildTraceInput(TOOL_NAME, schema, input),
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            if (e instanceof AgentToolException toolException) {
                throw toolException;
            }
            throw new RuntimeException("面试准备工具调用失败: " + e.getMessage(), e);
        }
    }
}
