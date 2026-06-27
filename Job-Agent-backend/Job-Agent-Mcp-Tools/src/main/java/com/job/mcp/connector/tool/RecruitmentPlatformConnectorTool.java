package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 招聘平台连接器工具。
 * 第一版只提供外部岗位搜索预览，不直接爬取或调用招聘平台，避免账号授权和平台风控问题。
 */
@Component
public class RecruitmentPlatformConnectorTool {

    private static final String SEARCH_TOOL_NAME = "RecruitmentPlatformConnectorTool.searchExternalJobs";

    @Tool("从外部招聘平台搜索岗位。第一版只返回调用预览，不真正请求招聘平台 API。")
    public String searchExternalJobs(
            @P("招聘平台编码，例如 boss、liepin、lagou") String providerCode,
            @P("岗位关键词，例如 Java、产品经理") String keyword,
            @P("城市，例如 上海、北京、杭州") String city,
            @P("最多返回数量") Integer limit
    ) {
        /*
         * 步骤 1：把自然语言里的平台、关键词、城市、数量整理成稳定结构。
         * 步骤 2：返回 PREVIEW，让 Planner/Executor 知道下一步需要真实平台适配器。
         * 步骤 3：第二版可在这里委托 RecruitmentPlatformAdapter 发起真实 API 请求。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "providerCode", providerCode,
                "keyword", keyword,
                "city", city,
                "limit", limit == null ? 10 : limit
        );
        Map<String, Object> data = ConnectorResponseFactory.orderedRequest(
                "jobs", java.util.List.of(),
                "nextStep", "配置招聘平台真实适配器后，可同步外部岗位到本地岗位库"
        );
        return ConnectorResponseFactory.preview(
                SEARCH_TOOL_NAME,
                "recruitment_platform",
                providerCode,
                ConnectorSideEffectType.READ,
                false,
                "已生成外部招聘平台岗位搜索预览，第一版未调用真实招聘平台。",
                request,
                data
        );
    }
}
