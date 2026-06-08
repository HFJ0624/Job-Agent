package com.job.bootstrap.agent.tools;

import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.JobResumeScoreService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.vo.resume.ResumeScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:简历分析工具
 * 说明:
 * 1. Agent 不直接操作数据库。
 * 2. Agent 调用 Tool，Tool 再调用业务 Service。
 * 3. 这样方便测试、限权、记录日志和后续扩展。
 * 日期: 2026/6/8 15:12
 */
@Component
@RequiredArgsConstructor
public class ResumeAnalyzeTool {

    private final JobResumeScoreService jobResumeScoreService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;

    /**
     * 分析简历。
     *
     * @param resumeId 简历ID
     * @param targetPosition 目标岗位
     * @return JSON 字符串结果
     */
    @Tool("根据简历ID和目标岗位，对当前登录用户的简历进行整体评分，返回总分、维度分、优势、问题和优化建议")
    public String analyzeResume(
            @P("简历ID，例如 1") Long resumeId,
            @P("目标岗位名称，可以为空，例如 Java 后端开发") String targetPosition
    ) {
        long start = System.currentTimeMillis();
        Long userId = AgentUserContext.getRequiredUserId();

        Map<String, Object> input = Map.of(
                "resumeId", resumeId,
                "targetPosition", targetPosition
        );

        try {
            ResumeScoreVO result = jobResumeScoreService.scoreResume(userId, resumeId, targetPosition);

            String json = objectMapper.writeValueAsString(result);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    null,
                    "RESUME_ANALYZE",
                    "ResumeAnalyzeTool",
                    input,
                    result,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return json;
        } catch (Exception e) {
            /*
             * 工具调用失败，也记录 Trace，便于后台排查。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    null,
                    "RESUME_ANALYZE",
                    "ResumeAnalyzeTool",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            return "简历分析失败：" + e.getMessage();
        }
    }
}
