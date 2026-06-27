package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 岗位来源同步连接器工具。
 * 用于把外部招聘平台岗位同步到本地岗位库，第一版只返回同步预览。
 */
@Component
public class JobSourceSyncConnectorTool {

    private static final String SYNC_TOOL_NAME = "JobSourceSyncConnectorTool.syncJobs";

    @Tool("从外部岗位来源同步岗位到本地岗位库。第一版只返回同步预览，不写数据库。")
    public String syncJobs(
            @P("岗位来源编码，例如 boss、liepin、lagou") String providerCode,
            @P("同步关键词，例如 Java、AI 产品经理") String keyword,
            @P("同步城市") String city,
            @P("同步数量上限") Integer limit
    ) {
        /*
         * 岗位同步会写入本地数据库，也可能触发 RAG 增量索引，所以属于写操作。
         * 第一版只返回预览，第二版应放入工作流任务队列异步执行，并记录同步日志。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "providerCode", providerCode,
                "keyword", keyword,
                "city", city,
                "limit", limit == null ? 20 : limit
        );
        Map<String, Object> data = ConnectorResponseFactory.orderedRequest(
                "syncedCount", 0,
                "nextStep", "接入真实招聘平台适配器后，将岗位写入本地岗位库并触发索引"
        );
        return ConnectorResponseFactory.preview(
                SYNC_TOOL_NAME,
                "job_source_sync",
                providerCode,
                ConnectorSideEffectType.WRITE,
                true,
                "已生成岗位来源同步预览。真实同步前需要用户确认。",
                request,
                data
        );
    }
}
