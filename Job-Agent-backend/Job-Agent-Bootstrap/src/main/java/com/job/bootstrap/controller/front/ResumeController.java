package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.dto.resume.ResumeUploadDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.resume.ResumeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 作者:hfj
 * 功能:用户端简历接口，提供简历上传和我的简历列表查询
 * 日期:2026/6/4 10:30
 */
@Validated
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final JobResumeService jobResumeService;

    /**
     * 上传简历。
     * P表示参数描述，表单字段包含 resumeName 和 file；接口需要用户登录后才能访问。
     *
     * @param request 简历上传请求参数，包含简历名称
     * @param file 前端上传的简历文件
     * @return 返回上传成功后的简历信息
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ResumeVO> upload(@Valid @ModelAttribute ResumeUploadDTO request,
                                   @RequestParam("file") MultipartFile file) {
        // 1. 从 Sa-Token 读取当前登录用户 ID，保证简历只绑定到当前用户自己名下。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. DTO 转实体只保存用户 ID 和简历名称，文件信息由 Service 上传 MinIO 后补齐。
        JobResume resumeParam = request.toEntity(userId);

        // 3. 调用 Service 完成查重、文件上传和数据库保存。
        JobResume savedResume = jobResumeService.uploadResume(
                resumeParam.getUserId(),
                resumeParam.getResumeName(),
                file
        );

        // 4. 把数据库实体转换成 VO 返回给前端页面展示。
        return Result.build(ResumeVO.from(savedResume), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询我的简历列表。
     *
     * @return 返回当前登录用户上传过的简历列表
     */
    @GetMapping("/list")
    public Result<List<ResumeVO>> listMine() {
        // 1. 当前用户只能看到自己的简历，不能传 userId 查询别人数据。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 查询当前用户未删除的简历，并转换成前端需要的 VO。
        List<ResumeVO> records = jobResumeService.listUserResumes(userId)
                .stream()
                .map(ResumeVO::from)
                .toList();

        return Result.build(records, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查看或下载我的简历文件。
     * P表示参数描述，前端通过 fetch 带上 token 访问这个接口，避免直接访问私有 MinIO 桶时出现 403。
     *
     * @param id 简历 ID
     * @return 返回简历文件流
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> file(@PathVariable Long id) {
        // 1. 先校验这份简历属于当前登录用户，防止越权读取别人简历。
        Long userId = StpUtil.getLoginIdAsLong();
        JobResume resume = jobResumeService.getUserResumeRequired(userId, id);

        // 2. 从 MinIO 打开文件流，由 Spring MVC 直接写回浏览器。
        InputStream inputStream = jobResumeService.openResumeFile(resume);

        // 3. Content-Disposition 使用 inline，PDF 会优先预览，Word 文件通常由浏览器下载。
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(resolveMediaType(resume.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(resume.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString());

        if (resume.getFileSize() != null) {
            builder.contentLength(resume.getFileSize());
        }
        return builder.body(new InputStreamResource(inputStream));
    }

    /**
     * 根据文件类型返回浏览器能识别的 Content-Type。
     *
     * @param fileType 数据库保存的文件类型
     * @return 返回 HTTP Content-Type
     */
    private MediaType resolveMediaType(String fileType) {
        if ("PDF".equalsIgnoreCase(fileType)) {
            return MediaType.APPLICATION_PDF;
        }
        if ("DOC".equalsIgnoreCase(fileType)) {
            return MediaType.parseMediaType("application/msword");
        }
        if ("DOCX".equalsIgnoreCase(fileType)) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
