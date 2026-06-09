package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
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
    @Tool("""
            根据用户简历ID和岗位ID，分析简历与岗位的匹配度。
            当用户询问“是否适合岗位”“岗位匹配度”“是否建议投递”“岗位差距分析”时使用本工具。
            注意：userId 由系统上下文自动注入，不需要模型提供。
            """)
    public String matchJob(
            @P("用户选择的简历ID，必须由用户输入或前端上下文提供，不能编造") Long resumeId,
            @P("用户选择的岗位ID，必须由用户输入或前端上下文提供，不能编造") Long jobId
    ) {
        long start = System.currentTimeMillis();

        /*
         * 从后端上下文中获取当前用户ID。
         * 这样可以防止模型伪造 userId，避免越权访问别人的简历。
         */
        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = Map.of(
                "resumeId", resumeId,
                "jobId", jobId
        );
        try {
            /*
             * 调用已有的岗位匹配服务。
             * 注意:
             * 这里不要在 Tool 中重复写匹配算法。
             * Tool 只是 Agent 与业务服务之间的桥。
             */
            JobMatchVO result = jobMatchService.matchJob(userId, resumeId, jobId);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    "JobMatchTool.matchJob",
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
                    conversationId,
                    intentCode,
                    "JobMatchTool.matchJob",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            /*
             * 把异常继续抛给 AgentChatServiceImpl。
             * AgentChatServiceImpl 会记录主链路失败日志。
             */
            throw new RuntimeException("岗位匹配工具调用失败: " + e.getMessage(), e);
        }
    }
}
