package com.job.mcp.connector.tool;

import com.job.common.agent.AgentRuntimeContext;
import com.job.exception.BizException;
import com.job.mcp.connector.model.ConnectorSideEffectType;
import com.job.mcp.connector.support.ConnectorResponseFactory;
import com.job.mcp.connector.support.ResumeExportExecutor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历导出连接器工具（任务 J3 真实化）。
 *
 * 设计说明:
 * 1. 三分支响应：未确认/后台预览 → PREVIEW；已确认且真实执行成功 → EXECUTED（带可核对文件信息）；
 *    参数/归属/存储失败 → ERROR（结构化原因，而不是把异常堆栈抛给 Agent）；
 * 2. 预览与执行的分界：AgentRuntimeContext.isToolConfirmed——Executor 只在用户确认后才调工具
 *    （chatInternal 的确认门禁），后台预览按钮与 MCP 调试调用没有确认上下文，天然只到预览；
 * 3. 安全边界：真实执行必须带运行时 userId（模型传参不算数），归属校验在 Bootstrap 实现里做；
 * 4. v1 仅 Markdown：PDF/DOCX 需要文档生成器适配器，接入前明确告知，不谎称支持。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeExportConnectorTool {

    private static final String EXPORT_TOOL_NAME = "ResumeExportConnectorTool.exportResume";
    private static final String CONNECTOR_TYPE = "resume_export";
    private static final String PROVIDER_CODE = "local-generator";
    private static final ConnectorSideEffectType SIDE_EFFECT = ConnectorSideEffectType.EXPORT;

    /** v1 支持的导出格式（PDF/DOCX 待文档生成器适配器）。 */
    private static final String SUPPORTED_FORMAT = "MARKDOWN";

    private final ResumeExportExecutor resumeExportExecutor;

    @Tool("导出用户简历为 Markdown 文件。归属校验通过后：未确认时返回导出预览；用户确认后生成真实文件并上传对象存储，返回可核对的文件键与下载地址。PDF/DOCX 暂未接入文档生成器。")
    public String exportResume(
            @P("简历ID") Long resumeId,
            @P("导出格式，例如 MARKDOWN（v1 仅支持 Markdown）") String format
    ) {
        Map<String, Object> request = ConnectorResponseFactory.orderedRequest(
                "resumeId", resumeId,
                "format", format
        );

        // 1. 参数校验：ID 必填、格式必须受支持（空格式默认 Markdown）
        if (resumeId == null || resumeId <= 0) {
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    "简历ID不合法，无法导出。", request);
        }
        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    "导出格式暂不支持（v1 仅支持 Markdown），请使用 MARKDOWN。", request);
        }

        // 2. 未确认（后台预览 / MCP 调试 / 模型直呼）→ 只预览，不执行
        if (!AgentRuntimeContext.isToolConfirmed(EXPORT_TOOL_NAME)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resumeId", resumeId);
            data.put("format", normalizedFormat);
            data.put("estimatedFileName", "resume-" + resumeId + ".md");
            data.put("nextStep", "用户确认后生成真实 Markdown 文件并上传对象存储。");
            return ConnectorResponseFactory.preview(
                    EXPORT_TOOL_NAME,
                    CONNECTOR_TYPE,
                    PROVIDER_CODE,
                    SIDE_EFFECT,
                    true,
                    "已生成简历导出预览，确认后执行真实导出。",
                    request,
                    data
            );
        }

        // 3. 真实执行：userId 只信运行时上下文（模型传参不算数）
        Long userId;
        try {
            userId = AgentRuntimeContext.getRequiredUserId();
        } catch (Exception exception) {
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    "导出缺少运行时用户上下文，拒绝执行。", request);
        }
        if (userId == null || userId <= 0) {
            // 上下文存在但 userId 缺失/非法：同样拒绝，不把脏请求传给执行器
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    "导出缺少有效用户身份，拒绝执行。", request);
        }

        try {
            ResumeExportExecutor.ExportResult result =
                    resumeExportExecutor.export(userId, resumeId, normalizedFormat);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resumeName", result.resumeName());
            data.put("filename", result.filename());
            data.put("fileKey", result.fileKey());
            data.put("fileUrl", result.fileUrl());
            data.put("fileSize", result.fileSize());
            data.put("format", normalizedFormat);
            return ConnectorResponseFactory.executed(
                    EXPORT_TOOL_NAME,
                    CONNECTOR_TYPE,
                    PROVIDER_CODE,
                    SIDE_EFFECT,
                    "简历已导出为 Markdown 文件，可通过 fileUrl 核对内容。",
                    request,
                    data
            );
        } catch (BizException exception) {
            // 归属校验失败 / 存储失败：结构化错误，不泄露简历存在性细节
            log.info("简历导出失败，userId={}, resumeId={}, error={}", userId, resumeId, exception.getMessage());
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    exception.getMessage(), request);
        } catch (Exception exception) {
            log.warn("简历导出异常，userId={}, resumeId={}, error={}", userId, resumeId, exception.getMessage());
            return ConnectorResponseFactory.error(
                    EXPORT_TOOL_NAME, CONNECTOR_TYPE, PROVIDER_CODE, SIDE_EFFECT,
                    "导出失败，请稍后重试。", request);
        }
    }

    // 格式归一化：空 → MARKDOWN；MD/MARKDOWN → MARKDOWN；其他 → null（不支持）
    private String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return SUPPORTED_FORMAT;
        }
        String upper = format.trim().toUpperCase();
        if ("MD".equals(upper) || "MARKDOWN".equals(upper)) {
            return SUPPORTED_FORMAT;
        }
        return null;
    }
}
