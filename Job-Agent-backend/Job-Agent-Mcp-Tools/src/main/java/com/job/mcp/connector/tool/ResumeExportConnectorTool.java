package com.job.mcp.connector.tool;

import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 简历导出连接器工具。
 * 第一版只定义导出意图，后续可接入 PDF、Word、Markdown 生成器和对象存储。
 */
@Component
public class ResumeExportConnectorTool {

    private static final String EXPORT_TOOL_NAME = "ResumeExportConnectorTool.exportResume";

    @Tool("导出用户简历为 PDF、Word 或 Markdown。第一版只返回导出预览。")
    public String exportResume(
            @P("简历ID") Long resumeId,
            @P("导出格式，例如 PDF、DOCX、MARKDOWN") String format
    ) {
        /*
         * 导出本身通常不会触达外部用户，但会生成文件，因此标记为 EXPORT。
         * 真实实现时需要校验 resumeId 属于当前用户，并对导出链接设置过期时间。
         */
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "resumeId", resumeId,
                "format", format
        );
        Map<String, Object> data = ConnectorResponseFactory.orderedRequest(
                "downloadUrl", null,
                "nextStep", "接入文档生成器后，可返回临时下载链接"
        );
        return ConnectorResponseFactory.preview(
                EXPORT_TOOL_NAME,
                "resume_export",
                "local-generator",
                ConnectorSideEffectType.EXPORT,
                false,
                "已生成简历导出预览，第一版未生成真实文件。",
                request,
                data
        );
    }
}
