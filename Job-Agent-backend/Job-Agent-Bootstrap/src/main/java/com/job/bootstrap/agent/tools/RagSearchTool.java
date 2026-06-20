package com.job.bootstrap.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.exception.AgentToolException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:RAG 知识库检索工具
 * 日期:2026/6/14
 */
@Component
@RequiredArgsConstructor
public class RagSearchTool {

    private static final String TOOL_NAME = "RagSearchTool.searchKnowledge";

    private final RagRetrievalService ragRetrievalService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;
    private final AgentToolGuard agentToolGuard;

    /**
     * 检索当前用户相关的简历、岗位、公司和沟通记录。
     *
     * @param query 检索问题
     * @param limit 召回条数
     * @return RAG 召回结果 JSON
     */
    @Tool("""
            检索当前登录用户的 RAG 求职知识库。
            当用户询问自己的简历经历、项目经历、某个 JD/岗位要求、公司信息、HR 沟通历史、面试邀约、跟进记录时，优先使用本工具。
            工具会同时检索公共岗位/公司知识和当前用户私有的简历/沟通知识，不需要模型提供 userId。
            """)
    public String searchKnowledge(
            @P("检索问题，例如：我的 Java 项目经历和这个岗位匹配吗？HR 上次回复了什么？这家公司主要做什么？") String query,
            @P("召回条数，建议 3 到 5；可以为空") Integer limit
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        input.put("limit", limit);

        AgentToolSchema schema = null;
        try {
            schema = agentToolGuard.validate(TOOL_NAME, input);
            Map<String, Object> traceInput = agentToolGuard.buildTraceInput(TOOL_NAME, schema, input);

            /*
             * 1. Tool 不直接拼 SQL，也不直接操作向量库。
             * 2. Tool 只调用领域服务，便于记录 Trace、做权限隔离、后续替换检索策略。
             */
            List<RagSearchResultVO> results = ragRetrievalService.search(userId, query, limit);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("total", results.size());
            output.put("results", results);

            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    TOOL_NAME,
                    traceInput,
                    output,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
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
            throw new RuntimeException("RAG 知识库检索工具调用失败: " + e.getMessage(), e);
        }
    }
}
