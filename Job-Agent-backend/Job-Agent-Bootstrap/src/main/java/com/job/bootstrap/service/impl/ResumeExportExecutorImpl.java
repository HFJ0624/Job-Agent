package com.job.bootstrap.service.impl;

import com.job.bootstrap.service.FileStorageService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import com.job.exception.BizException;
import com.job.mcp.connector.support.ResumeExportExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 功能: 简历导出执行器实现（任务 J3）。
 *
 * 设计说明:
 * 1. 归属校验：getUserResumeRequired(userId, resumeId) 未找到或不属于该用户都抛错，
 *    且错误信息不区分“不存在”与“越权”，避免泄露简历存在性；
 * 2. v1 只支持 Markdown：内容由简历名称 + 原文/结构化解析结果拼装，不引入 PDF/DOCX
 *    生成器（那是独立的真实适配器，接入前不谎称支持）；
 * 3. 幂等：对象键固定为 "{userId}/{resumeId}.md"——同一简历重复导出覆盖同一对象，
 *    内容一致即无副作用累积；存储失败抛 BizException，由工具转结构化 ERROR 响应。
 */
@Slf4j
@Service
public class ResumeExportExecutorImpl implements ResumeExportExecutor {

    private static final String STORAGE_FOLDER = "resume-export";
    private static final String CONTENT_TYPE = "text/markdown";

    private final JobResumeService jobResumeService;
    private final FileStorageService fileStorageService;

    public ResumeExportExecutorImpl(JobResumeService jobResumeService, FileStorageService fileStorageService) {
        this.jobResumeService = jobResumeService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public ExportResult export(Long userId, Long resumeId, String format) {
        if (userId == null || userId <= 0 || resumeId == null || resumeId <= 0) {
            throw new BizException("导出参数不合法：userId 与 resumeId 必填");
        }

        // 1. 归属校验（不存在/越权同一错误信息）
        JobResume resume = jobResumeService.getUserResumeRequired(userId, resumeId);

        // 2. 生成 Markdown 内容
        String filename = buildFilename(resume);
        byte[] content = buildMarkdown(resume).getBytes(StandardCharsets.UTF_8);

        // 3. 稳定对象键上传（幂等）
        FileStorageService.StoredFile stored = fileStorageService.uploadBytes(
                content, filename, CONTENT_TYPE, STORAGE_FOLDER, userId + "/" + resumeId + ".md");

        log.info("简历导出完成，userId={}, resumeId={}, fileKey={}, size={}",
                userId, resumeId, stored.objectName(), stored.fileSize());

        return new ExportResult(
                resume.getResumeName(),
                filename,
                stored.objectName(),
                stored.fileUrl(),
                stored.fileSize(),
                stored.contentType()
        );
    }

    private String buildFilename(JobResume resume) {
        String name = StringUtils.hasText(resume.getResumeName()) ? resume.getResumeName().trim() : "简历";
        // 文件名去掉路径不安全字符，避免对象键混乱
        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safeName + ".md";
    }

    /*
     * Markdown 内容：标题 + 元信息 + 原文优先，原文缺失时回退结构化解析结果。
     * 只做拼装不做改写——导出必须忠于简历原文（任务 J5 同源要求）。
     */
    private String buildMarkdown(JobResume resume) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(StringUtils.hasText(resume.getResumeName()) ? resume.getResumeName().trim() : "我的简历").append("\n\n");
        builder.append("> 由 Job-Agent 导出 · 格式 Markdown\n\n");
        builder.append("## 简历内容\n\n");
        if (StringUtils.hasText(resume.getRawText())) {
            builder.append(resume.getRawText().trim()).append("\n");
        } else if (StringUtils.hasText(resume.getParsedJson())) {
            builder.append("```json\n").append(resume.getParsedJson().trim()).append("\n```\n");
        } else {
            builder.append("（暂无简历正文）\n");
        }
        return builder.toString();
    }
}
