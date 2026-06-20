package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobGreetingService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.greeting.GreetingVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:HR 打招呼语生成工具
 * 使用场景:
 * 1. 用户说“帮我生成一段给 HR 的打招呼语”
 * 2. 用户说“这个岗位怎么跟 HR 开场”
 * 3. 用户说“帮我写一段更礼貌的求职开场白”
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class GreetingGenerateTool {

    private static final String TOOL_NAME = "GreetingGenerateTool.generateGreeting";

    private final JobGreetingService jobGreetingService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;

    /**
     * 生成 HR 打招呼语。
     *
     * @param resumeId 简历ID
     * @param jobId 岗位ID
     * @param style 语气风格
     * @return 打招呼语 JSON
     */
    @Tool("""
            根据用户简历和岗位生成 HR 打招呼语。
            当用户要求“生成打招呼语”“HR 开场白”“沟通话术”时使用本工具。
            style 可以是: 礼貌、简洁、积极、正式。
            resumeId 和 jobId 必须由用户输入或前端上下文提供，不能编造。
            """
    )
    public String generateGreeting(
            @P("用户选择的简历ID，不能编造") Long resumeId,
            @P("用户选择的岗位ID，不能编造") Long jobId,
            @P("语气风格，例如 礼貌、简洁、积极、正式，可以为空") String style
    )  {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("resumeId", resumeId);
        input.put("jobId", jobId);
        input.put("style", style == null ? "礼貌" : style);

        AgentToolSchema schema = null;
        try {
            /*
             * 该工具会自动创建沟通记录，Schema 中要求用户确认。
             * 如果本轮请求没有 confirmedToolNames，Guard 会阻止执行。
             */
            schema = agentToolGuard.validate(TOOL_NAME, input);
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            GreetingVO result = jobGreetingService.generateGreeting(
                    userId,
                    resumeId,
                    jobId,
                    style == null ? "礼貌" : style
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
            throw new RuntimeException("打招呼语生成工具调用失败: " + e.getMessage(), e);
        }
    }
}
