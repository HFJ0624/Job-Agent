package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobMatchService;
import com.job.common.vo.match.JobMatchVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:岗位匹配工具
 * 日期: 2026/6/8 15:14
 */
@Component
@RequiredArgsConstructor
public class JobMatchTool {

    private final JobMatchService jobMatchService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;

    /**
     * 分析当前登录用户某份简历与岗位的匹配度。
     */
    @Tool("根据简历ID和岗位ID，计算当前登录用户的简历与岗位的匹配度，返回匹配分、匹配技能、缺失技能、风险点和建议")
    public String matchJob(
            @P("简历ID，例如 1") Long resumeId,
            @P("岗位ID，例如 1") Long jobId
    ) {
        long start = System.currentTimeMillis();
        Long userId = AgentUserContext.getRequiredUserId();

        Map<String, Object> input = Map.of(
                "resumeId", resumeId,
                "jobId", jobId
        );
        try {
            JobMatchVO result = jobMatchService.matchJob(userId, resumeId, jobId);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    null,
                    "JOB_MATCH",
                    "JobMatchTool",
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
                    "JOB_MATCH",
                    "JobMatchTool",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );
            return "岗位匹配失败：" + e.getMessage();
        }
    }
}
