package com.job.bootstrap.service.impl;

import com.job.bootstrap.service.FileStorageService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import com.job.exception.BizException;
import com.job.mcp.connector.support.ResumeExportExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResumeExportExecutorImpl 单元测试（任务 J3）。
 *
 * 覆盖重点:
 * 1. 正常路径：归属校验通过 → Markdown 内容 → 稳定对象键上传 → 返回可核对结果；
 * 2. 幂等：同一 userId + resumeId 使用固定对象键（重复导出覆盖同一对象）；
 * 3. 归属失败（越权/不存在）→ BizException 抛出，不调存储；
 * 4. 存储失败 → BizException 包装抛出；
 * 5. 内容忠于原文：rawText 优先，缺失回退 parsedJson。
 */
class ResumeExportExecutorImplTest {

    private final JobResumeService jobResumeService = mock(JobResumeService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final ResumeExportExecutorImpl executor =
            new ResumeExportExecutorImpl(jobResumeService, fileStorageService);

    private JobResume resume() {
        JobResume resume = new JobResume();
        resume.setId(1001L);
        resume.setUserId(100L);
        resume.setResumeName("张三简历");
        resume.setRawText("张三，Java 后端，3 年经验");
        return resume;
    }

    @BeforeEach
    void setUp() {
        when(jobResumeService.getUserResumeRequired(100L, 1001L)).thenReturn(resume());
        when(fileStorageService.uploadBytes(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new FileStorageService.StoredFile(
                        "http://minio/resume-export/100/1001.md", "resume-export/100/1001.md",
                        "张三简历.md", 2048L, "text/markdown"));
    }

    @Test
    void shouldExportWithStableObjectKey() {
        ResumeExportExecutor.ExportResult result = executor.export(100L, 1001L, "MARKDOWN");

        assertThat(result.resumeName()).isEqualTo("张三简历");
        assertThat(result.fileKey()).isEqualTo("resume-export/100/1001.md");
        assertThat(result.fileUrl()).isEqualTo("http://minio/resume-export/100/1001.md");
        // 稳定键 = {userId}/{resumeId}.md：重复导出覆盖同一对象（幂等）
        verify(fileStorageService).uploadBytes(any(), eq("张三简历.md"),
                eq("text/markdown"), eq("resume-export"), eq("100/1001.md"));
    }

    @Test
    void contentShouldBeFaithfulToRawText() {
        executor.export(100L, 1001L, "MARKDOWN");

        org.mockito.ArgumentCaptor<byte[]> captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).uploadBytes(captor.capture(), anyString(), anyString(), anyString(), anyString());
        String markdown = new String(captor.getValue(), StandardCharsets.UTF_8);
        assertThat(markdown).contains("# 张三简历");
        assertThat(markdown).contains("张三，Java 后端，3 年经验");
    }

    @Test
    void missingRawTextShouldFallbackToParsedJson() {
        JobResume resume = resume();
        resume.setRawText(null);
        resume.setParsedJson("{\"name\":\"张三\"}");
        when(jobResumeService.getUserResumeRequired(100L, 1001L)).thenReturn(resume);

        executor.export(100L, 1001L, "MARKDOWN");

        org.mockito.ArgumentCaptor<byte[]> captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).uploadBytes(captor.capture(), anyString(), anyString(), anyString(), anyString());
        String markdown = new String(captor.getValue(), StandardCharsets.UTF_8);
        assertThat(markdown).contains("{\"name\":\"张三\"}");
    }

    @Test
    void ownershipFailureShouldThrowAndSkipStorage() {
        when(jobResumeService.getUserResumeRequired(100L, 1001L))
                .thenThrow(new BizException("简历不存在或无权访问"));

        assertThatThrownBy(() -> executor.export(100L, 1001L, "MARKDOWN"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("简历不存在或无权访问");
        verify(fileStorageService, never()).uploadBytes(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void storageFailureShouldThrow() {
        when(fileStorageService.uploadBytes(any(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new BizException("文件上传失败: minio 不可用"));

        assertThatThrownBy(() -> executor.export(100L, 1001L, "MARKDOWN"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件上传失败");
    }

    @Test
    void invalidParamsShouldThrow() {
        assertThatThrownBy(() -> executor.export(null, 1001L, "MARKDOWN"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> executor.export(100L, null, "MARKDOWN"))
                .isInstanceOf(BizException.class);
    }
}
