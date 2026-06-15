package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.dto.resume.ResumeScoreRequestDTO;
import com.job.common.dto.resume.ResumeUpdateDTO;
import com.job.common.dto.resume.ResumeUploadDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.resume.ResumeScoreVO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/front/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final JobResumeService jobResumeService;

    private final JobResumeScoreService jobResumeScoreService;

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
     * 修改我的简历名称。
     * P表示参数描述，简历 ID 来自路径，新的简历名称来自请求体。
     *
     * @param id 简历 ID
     * @param request 修改名称请求参数
     * @return 返回修改后的简历信息
     */
    @PutMapping("/{id}")
    public Result<ResumeVO> updateName(@PathVariable Long id,
                                       @Valid @RequestBody ResumeUpdateDTO request) {
        // 1. 用户 ID 始终从 token 中读取，避免前端传 userId 造成越权风险。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 调用 Service 完成归属校验、名称查重和数据库更新。
        JobResume updatedResume = jobResumeService.updateResumeName(userId, id, request.getResumeName());
        return Result.build(ResumeVO.from(updatedResume), ResultCodeEnum.SUCCESS);
    }

    /**
     * 逻辑删除我的简历。
     * P表示参数描述，只把数据库 isDeleted 改成 1，不直接删除 MinIO 文件。
     *
     * @param id 简历 ID
     * @return 返回删除成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Object> delete(@PathVariable Long id) {
        // 1. 当前用户只能删除自己名下的简历，具体校验在 Service 里完成。
        Long userId = StpUtil.getLoginIdAsLong();
        jobResumeService.deleteResume(userId, id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 设置我的默认简历。
     * P表示参数描述，一个用户只能有一份默认简历。
     *
     * @param id 简历 ID
     * @return 返回设置后的默认简历信息
     */
    @PutMapping("/{id}/default")
    public Result<ResumeVO> setDefault(@PathVariable Long id) {
        // 1. 当前用户只能把自己名下的简历设置为默认简历。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. Service 会先取消其它简历默认状态，再把目标简历设为默认。
        JobResume defaultResume = jobResumeService.setDefaultResume(userId, id);
        return Result.build(ResumeVO.from(defaultResume), ResultCodeEnum.SUCCESS);
    }

    /**
     * 解析我的简历文本。
     * P表示参数描述，后端会读取 MinIO 文件，把抽取出的文本保存到 resume.raw_text 字段。
     *
     * @param id 简历 ID
     * @return 返回解析后的简历信息；解析失败时 status 为 PARSE_FAILED，rawText 保存失败原因
     */
    @PostMapping("/{id}/parse")
    public Result<ResumeVO> parse(@PathVariable Long id) {
        // 1. 当前用户只能解析自己名下的简历，不能通过改 ID 解析别人的文件。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. Service 会完成 MinIO 读取、Tika 文本抽取、乱码检测和数据库保存。
        JobResume parsedResume = jobResumeService.parseResumeText(userId, id);
        return Result.build(ResumeVO.from(parsedResume), ResultCodeEnum.SUCCESS);
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
     * 对我的简历进行评分。
     * P表示参数描述：如果简历还没有解析文本，Service 会自动先调用解析逻辑。
     *
     * @param id 简历ID
     * @param request 评分请求参数，可传求职方向
     * @return 返回简历评分结果
     */
    @PostMapping("/{id}/score")
    public Result<ResumeScoreVO> score(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ResumeScoreRequestDTO request
    ) {
        // 1. 当前用户只能评分自己名下的简历。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. request 允许为空，避免前端不传求职方向时报错。
        String targetPosition = request == null ? null : request.getTargetPosition();

        // 3. 调用评分服务，生成评分记录并同步更新 resume.score。
        ResumeScoreVO score = jobResumeScoreService.scoreResume(userId, id, targetPosition);

        return Result.build(score, ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询我的简历最近一次评分结果。
     *
     * @param id 简历ID
     * @return 返回最近一次评分结果；没有评分记录时 data 为 null
     */
    @GetMapping("/{id}/score")
    public Result<ResumeScoreVO> latestScore(@PathVariable Long id) {
        // 1. 用户ID从登录态获取，不能从前端传入。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 查询最近一次评分记录。
        ResumeScoreVO score = jobResumeScoreService.getLatestScore(userId, id);

        return Result.build(score, ResultCodeEnum.SUCCESS);
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
