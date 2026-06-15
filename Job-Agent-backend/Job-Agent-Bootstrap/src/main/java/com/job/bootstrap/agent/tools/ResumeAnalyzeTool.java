package com.job.bootstrap.agent.tools;

import com.job.bootstrap.agent.context.AgentRuntimeContext;
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
     * @param targetPosition 求职方向
     * @return JSON 字符串结果
     */
    @Tool("""
        对当前登录用户的指定简历进行 AI 简历质量评分 V2。
        这个工具不是岗位匹配评分，而是评价简历本身质量，返回总分、八个维度分、优势、不足、风险点、优化建议和总结。
        当用户要求“分析简历”“简历评分”“优化简历”“看看简历问题”“简历质量怎么样”时使用本工具。
        resumeId 必须由用户输入或由前端上下文提供，不能编造；targetPosition 可以为空，只表示用户的求职方向，不表示具体 JD。
    """)
    public String analyzeResume(
            @P("简历ID，例如 1") Long resumeId,
            @P("求职方向，可以为空，例如 Java 后端开发、AI Agent 开发") String targetPosition
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

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
                    conversationId,
                    intentCode,
                    "ResumeAnalyzeTool.analyzeResume",
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
                    conversationId,
                    intentCode,
                    "ResumeAnalyzeTool.analyzeResume",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            throw new RuntimeException("简历分析失败: " + e.getMessage(), e);
        }
    }
}
