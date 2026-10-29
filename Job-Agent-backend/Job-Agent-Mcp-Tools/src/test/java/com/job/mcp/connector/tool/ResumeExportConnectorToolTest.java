package com.job.mcp.connector.tool;

import com.job.common.agent.AgentRuntimeContext;
import com.job.exception.BizException;
import com.job.mcp.connector.support.ResumeExportExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeExportConnectorTool 单元测试（任务 J3）。
 *
 * 覆盖重点:
 * 1. 未确认（无运行时确认上下文）→ PREVIEW，不调执行器；
 * 2. 已确认 → EXECUTED，执行器被调用且响应带可核对文件信息；
 * 3. 参数非法（resumeId 缺失）→ ERROR；
 * 4. 格式不支持（PDF）→ ERROR 并提示仅支持 Markdown；
 * 5. 归属校验失败（BizException）→ ERROR，错误信息不透出存在性细节；
 * 6. 确认但缺运行时 userId → ERROR，拒绝执行。
 */
class ResumeExportConnectorToolTest {

    private final ResumeExportExecutor executor = mock(ResumeExportExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeExportConnectorTool tool = new ResumeExportConnectorTool(executor);

    @BeforeEach
    void setUp() {
        AgentRuntimeContext.clear();
    }

    @AfterEach
    void tearDown() {
        AgentRuntimeContext.clear();
    }

    private JsonNode call(Long resumeId, String format) throws Exception {
        return objectMapper.readTree(tool.exportResume(resumeId, format));
    }

    @Test
    void unconfirmedShouldReturnPreviewWithoutExecution() throws Exception {
        // 无运行时上下文（后台预览场景）→ 只预览
        JsonNode response = call(1001L, "MARKDOWN");

        assertThat(response.get("status").asText()).isEqualTo("PREVIEW");
        assertThat(response.get("requiresRealAdapter").asBoolean()).isTrue();
        assertThat(response.get("requiresUserConfirmation").asBoolean()).isTrue();
        assertThat(response.get("data").get("estimatedFileName").asText()).isEqualTo("resume-1001.md");
        verify(executor, never()).export(any(), any(), any());
    }

    @Test
    void confirmedShouldExecuteAndReturnFileInfo() throws Exception {
        AgentRuntimeContext.set(100L, 10L, "trace-j3", "JOB_SEEK_FLOW",
                java.util.Set.of("ResumeExportConnectorTool.exportResume"));
        when(executor.export(100L, 1001L, "MARKDOWN")).thenReturn(
                new ResumeExportExecutor.ExportResult(
                        "张三简历", "张三简历.md", "resume-export/100/1001.md",
                        "http://minio/resume-export/100/1001.md", 2048L, "text/markdown"));

        JsonNode response = call(1001L, "MARKDOWN");

        assertThat(response.get("status").asText()).isEqualTo("EXECUTED");
        assertThat(response.get("requiresRealAdapter").asBoolean()).isFalse();
        assertThat(response.get("data").get("fileKey").asText()).isEqualTo("resume-export/100/1001.md");
        assertThat(response.get("data").get("fileSize").asLong()).isEqualTo(2048L);
        verify(executor).export(100L, 1001L, "MARKDOWN");
    }

    @Test
    void invalidResumeIdShouldReturnError() throws Exception {
        JsonNode response = call(null, "MARKDOWN");

        assertThat(response.get("status").asText()).isEqualTo("ERROR");
        verify(executor, never()).export(any(), any(), any());
    }

    @Test
    void unsupportedFormatShouldReturnError() throws Exception {
        JsonNode response = call(1001L, "PDF");

        assertThat(response.get("status").asText()).isEqualTo("ERROR");
        assertThat(response.get("message").asText()).contains("Markdown");
        verify(executor, never()).export(any(), any(), any());
    }

    @Test
    void ownershipFailureShouldReturnError() throws Exception {
        AgentRuntimeContext.set(100L, 10L, "trace-j3", "JOB_SEEK_FLOW",
                java.util.Set.of("ResumeExportConnectorTool.exportResume"));
        when(executor.export(anyLong(), anyLong(), anyString())).thenThrow(new BizException("简历不存在或无权访问"));

        JsonNode response = call(1001L, "MARKDOWN");

        assertThat(response.get("status").asText()).isEqualTo("ERROR");
        assertThat(response.get("message").asText()).contains("简历不存在或无权访问");
    }

    @Test
    void confirmedButMissingRuntimeUserShouldReturnError() throws Exception {
        // 确认了工具但没有 userId（异常上下文）→ 拒绝执行
        AgentRuntimeContext.set(null, 10L, "trace-j3", "JOB_SEEK_FLOW",
                java.util.Set.of("ResumeExportConnectorTool.exportResume"));

        JsonNode response = call(1001L, "MARKDOWN");

        assertThat(response.get("status").asText()).isEqualTo("ERROR");
        verify(executor, never()).export(any(), any(), any());
    }

    @Test
    void blankFormatShouldDefaultToMarkdown() throws Exception {
        AgentRuntimeContext.set(100L, 10L, "trace-j3", "JOB_SEEK_FLOW",
                java.util.Set.of("ResumeExportConnectorTool.exportResume"));
        when(executor.export(100L, 1001L, "MARKDOWN")).thenReturn(
                new ResumeExportExecutor.ExportResult(
                        "张三简历", "张三简历.md", "resume-export/100/1001.md", "url", 10L, "text/markdown"));

        JsonNode response = call(1001L, "  ");

        assertThat(response.get("status").asText()).isEqualTo("EXECUTED");
        verify(executor).export(100L, 1001L, "MARKDOWN");
    }
}
