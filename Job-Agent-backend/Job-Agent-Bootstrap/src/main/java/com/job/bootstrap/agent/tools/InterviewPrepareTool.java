package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.common.vo.interview.InterviewPrepareVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:AI 面试准备工具
 */
@Component
@RequiredArgsConstructor
public class InterviewPrepareTool {

    private final InterviewPrepareService interviewPrepareService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;

    /**
     * 根据求职记录生成面试准备。
     */
    @Tool("根据求职记录ID和简历ID，为当前登录用户生成面试准备题、项目追问题、HR问题和复习建议")
    public String prepareInterview(
            @P("求职记录ID，例如 1") Long applicationId,
            @P("简历ID，可以为空，例如 1") Long resumeId
    ) {
        long start = System.currentTimeMillis();
        Long userId = AgentUserContext.getRequiredUserId();

        Map<String, Object> input = Map.of(
                "applicationId", applicationId,
                "resumeId", resumeId
        );

        try {

            InterviewPrepareVO vo = interviewPrepareService.generatePrepare(
                    userId,
                    applicationId,
                    resumeId
            );

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    null,
                    "INTERVIEW_PREPARE_TOOL",
                    "InterviewPrepareTool",
                    input,
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
                    null,
                    "INTERVIEW_PREPARE_TOOL",
                    "InterviewPrepareTool",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            return "面试准备生成失败：" + e.getMessage();
        }
    }
}
