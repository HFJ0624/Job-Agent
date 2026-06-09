package com.job.bootstrap.agent.tools;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.vo.preference.JobRecommendVO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:岗位推荐工具
 * 说明:
 * 1. Agent 可以通过这个工具根据用户求职偏好推荐岗位。
 * 2. userId 从 AgentUserContext 获取，不让大模型生成。
 */
@Component
@RequiredArgsConstructor
public class JobRecommendTool {

    private final UserJobPreferenceService userJobPreferenceService;
    private final ObjectMapper objectMapper;
    private final AgentTraceService agentTraceService;

    /**
     * 根据当前用户求职偏好推荐岗位。
     *
     * @param keyword 岗位关键词，例如 Java 后端，可以为空
     * @param city 城市，例如 上海，可以为空
     * @param limit 推荐数量
     * @return 推荐岗位 JSON
     */
    @Tool("根据当前登录用户的求职偏好、岗位关键词和城市推荐岗位，返回推荐分、推荐理由和岗位信息")
    public String recommendJobs(
            @P("岗位关键词，例如 Java 后端、前端、AI应用开发，可以为空") String keyword,
            @P("城市，例如 上海、杭州、北京，可以为空") String city,
            @P("推荐数量，例如 5 或 10") Integer limit
    ) {
        long start = System.currentTimeMillis();

        Long userId = AgentRuntimeContext.getRequiredUserId();
        Long conversationId = AgentRuntimeContext.getConversationId();
        String intentCode = AgentRuntimeContext.getIntentCode();

        Map<String, Object> input = Map.of(
                "keyword", keyword,
                "city", city,
                "limit",limit
        );

        try {
            JobRecommendQueryDTO query = new JobRecommendQueryDTO();
            query.setKeyword(keyword);
            query.setCity(city);
            query.setLimit(limit == null ? 10 : limit);

            List<JobRecommendVO> list = userJobPreferenceService.recommendJobs(userId, query);

            /*
             * 工具调用成功，记录 Trace。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    "JobRecommendTool.recommendJobs",
                    input,
                    list,
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            /*
             * 工具调用失败，也记录 Trace，便于后台排查。
             */
            agentTraceService.saveToolTrace(
                    userId,
                    conversationId,
                    intentCode,
                    "JobRecommendTool.recommendJobs",
                    input,
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            throw new RuntimeException("岗位推荐失败: " + e.getMessage(), e);
        }
    }
}
