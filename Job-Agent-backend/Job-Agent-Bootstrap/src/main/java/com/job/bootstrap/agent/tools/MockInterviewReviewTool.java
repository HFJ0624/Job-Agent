package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:模拟面试复盘工具
 * 说明:
 * 1. Agent 可以通过该工具对某轮模拟面试进行复盘。
 * 2. userId 从 AgentUserContext 获取，避免让大模型生成用户ID。
 */
@Component
@RequiredArgsConstructor
public class MockInterviewReviewTool {

    private static final String TOOL_NAME = "MockInterviewReviewTool.generateMockInterviewReview";

    private final MockInterviewReviewService mockInterviewReviewService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;

    /**
     * 生成模拟面试复盘报告。
     */
    @Tool("""
        根据模拟面试会话ID生成复盘报告，包括总分、表现等级、优势、短板、薄弱题和提升计划
        当用户要求“复盘”“面试复盘”“复盘面试”时使用本工具。
        sessionId 必须由用户输入或前端上下文提供，不能编造。
        """)
    public String generateMockInterviewReview(
            @P("模拟面试会话ID，例如 1") Long sessionId
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", sessionId);

        AgentToolSchema schema = null;
        try {
            schema = agentToolGuard.validate(TOOL_NAME, input);
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            MockInterviewReviewVO vo = mockInterviewReviewService.generateReview(userId, sessionId);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    traceInput,
                    vo,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return objectMapper.writeValueAsString(vo);
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
            throw new RuntimeException("模拟面试复盘失败: " + e.getMessage(), e);
        }
    }
}
