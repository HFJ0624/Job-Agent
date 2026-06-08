package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobGreetingService;
import com.job.common.vo.greeting.GreetingVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:HR 打招呼语生成工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class GreetingGenerateTool {

    private final JobGreetingService jobGreetingService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;

    /**
     * 生成 HR 打招呼语。
     */
    @Tool("根据简历ID、岗位ID和语气风格，为当前登录用户生成适合发给 HR 的打招呼语")
    public String generateGreeting(
            @P("简历ID，例如 1") Long resumeId,
            @P("岗位ID，例如 1") Long jobId,
            @P("语气风格，例如 自然、正式、自信、实习生风格、社招风格、简洁直达") String style
    )  {
        long start = System.currentTimeMillis();
        Long userId = AgentUserContext.getRequiredUserId();

        Map<String, Object> input = Map.of(
                "resumeId", resumeId,
                "jobId", jobId,
                "style",style
        );

        try {
            GreetingVO result = jobGreetingService.generateGreeting(userId, resumeId, jobId, style);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    null,
                    "GREETING_GENERATE",
                    "GreetingGenerateTool",
                    input,
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
                    null,
                    "GREETING_GENERATE",
                    "GreetingGenerateTool",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            return "打招呼语生成失败：" + e.getMessage();
        }
    }
}
