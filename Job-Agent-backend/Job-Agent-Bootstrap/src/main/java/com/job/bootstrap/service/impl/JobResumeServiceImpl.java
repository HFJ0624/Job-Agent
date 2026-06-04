package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.config.MinioProperties;
import com.job.bootstrap.mapper.JobResumeMapper;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.resume.JobResume;
import com.job.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 作者:hfj
 * 功能:简历业务服务实现，处理简历查重、MinIO 上传和数据库保存
 * 日期:2026/6/4 10:30
 */
@Service
@RequiredArgsConstructor
public class JobResumeServiceImpl extends ServiceImpl<JobResumeMapper, JobResume> implements JobResumeService {

    /**
     * 简历最大文件大小，和 application-dev.yml 中 multipart.max-file-size 保持一致。
     */
    private static final long RESUME_MAX_SIZE = 10 * 1024 * 1024L;

    /**
     * 简历上传后最初的状态。
     */
    private static final String STATUS_UPLOADED = "UPLOADED";

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 不是默认简历。
     */
    private static final int NOT_DEFAULT = 0;

    /**
     * 允许上传的简历文件扩展名。
     */
    private static final Set<String> RESUME_EXTENSIONS = Set.of(".pdf", ".doc", ".docx");

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    /**
     * 上传简历。
     * P表示参数描述，文件会存入 MinIO 的 resume 目录，和 avatar 头像目录分开。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @param file 前端上传的简历文件
     * @return 返回保存后的简历实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobResume uploadResume(Long userId, String resumeName, MultipartFile file) {
        String cleanedResumeName = trimToNull(resumeName);
        String originalFilename = file == null ? null : normalizeOriginalFilename(file.getOriginalFilename());

        try {
            // 1. 先校验基础参数，避免空文件、超大文件、非简历格式文件进入后续流程。
            validateResumeFile(cleanedResumeName, file);

            // 2. 同一个用户下简历名称不能重复，防止前端列表里出现两个同名简历。
            if (existsSameResumeName(userId, cleanedResumeName)) {
                throw new BizException("简历名称已经存在，请换一个名称");
            }

            // 3. 确保存储桶存在，不存在时自动创建，方便本地开发环境第一次启动。
            ensureBucketExists();

            // 4. 生成 resume 专用目录下的对象名，并把文件流上传到 MinIO。
            String extension = getExtension(originalFilename);
            String objectName = buildResumeObjectName(userId, extension);
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .contentType(resolveContentType(file))
                        .stream(inputStream, file.getSize(), -1)
                        .build());
            }

            // 5. MinIO 上传成功后，再把文件元数据保存到 resume 表。
            Date now = new Date();
            JobResume resume = new JobResume();
            resume.setUserId(userId);
            resume.setResumeName(cleanedResumeName);
            resume.setFileUrl(buildFileUrl(objectName));
            resume.setFileName(originalFilename);
            resume.setFileType(extension.substring(1).toUpperCase());
            resume.setFileSize(file.getSize());
            resume.setStatus(STATUS_UPLOADED);
            resume.setIsDefault(NOT_DEFAULT);
            resume.setIsDeleted(NOT_DELETED);
            resume.setCreateTime(now);
            resume.setUpdateTime(now);

            save(resume);
            return resume;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            // 6. 系统异常要打印到后台控制台，便于快速定位 MinIO、数据库或文件流问题。
            printResumeUploadError(userId, cleanedResumeName, originalFilename, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "简历上传失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 查询当前用户的简历列表。
     *
     * @param userId 当前登录用户 ID
     * @return 返回该用户未删除的简历列表
     */
    @Override
    public List<JobResume> listUserResumes(Long userId) {
        // 1. 只查询当前用户未删除的简历，并按上传时间倒序展示。
        return list(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobResume::getCreateTime));
    }

    /**
     * 查询当前用户的指定简历。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回简历实体，不存在或不属于当前用户时抛出业务异常
     */
    @Override
    public JobResume getUserResumeRequired(Long userId, Long resumeId) {
        // 1. 查询条件固定带上 userId，避免用户通过改 URL 读取别人上传的简历。
        JobResume resume = getOne(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getId, resumeId)
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDeleted, NOT_DELETED), false);
        if (resume == null) {
            throw new BizException("简历不存在");
        }
        return resume;
    }

    /**
     * 从 MinIO 打开简历文件流。
     *
     * @param resume 数据库中的简历实体
     * @return 返回简历文件输入流，由 Controller 交给浏览器读取
     */
    @Override
    public InputStream openResumeFile(JobResume resume) {
        try {
            // 1. 数据库保存的是完整文件地址，这里解析出 MinIO 真正需要的 objectName。
            String objectName = resolveObjectName(resume.getFileUrl());
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            printResumeFileError(resume, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "简历文件读取失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 校验简历文件。
     *
     * @param resumeName 简历名称
     * @param file 前端上传的文件
     */
    private void validateResumeFile(String resumeName, MultipartFile file) {
        if (!StringUtils.hasText(resumeName)) {
            throw new BizException("简历名称不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的简历文件");
        }
        if (file.getSize() > RESUME_MAX_SIZE) {
            throw new BizException("简历文件不能超过10MB");
        }

        String extension = getExtension(normalizeOriginalFilename(file.getOriginalFilename()));
        if (!RESUME_EXTENSIONS.contains(extension)) {
            throw new BizException("简历只支持 PDF、DOC、DOCX 格式");
        }
    }

    /**
     * 判断同一个用户下是否已经存在同名简历。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @return true 表示已存在，false 表示不存在
     */
    private boolean existsSameResumeName(Long userId, String resumeName) {
        return count(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getResumeName, resumeName)
                .eq(JobResume::getIsDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 确保 MinIO 存储桶存在。
     */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
        }
    }

    /**
     * 生成简历文件在 MinIO 中的对象名。
     *
     * @param userId 当前登录用户 ID
     * @param extension 文件扩展名
     * @return 返回 resume 目录下的对象名
     */
    private String buildResumeObjectName(Long userId, String extension) {
        LocalDate now = LocalDate.now();
        return "resume/" + userId + "/" + now.getYear() + "/" + now.getMonthValue() + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 构造文件访问地址。
     *
     * @param objectName MinIO 对象名
     * @return 返回可保存到数据库的完整访问 URL
     */
    private String buildFileUrl(String objectName) {
        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }

    /**
     * 从数据库文件地址中解析 MinIO 对象名。
     *
     * @param fileUrl 数据库保存的文件地址
     * @return 返回 MinIO 对象名，例如 resume/1/2026/6/xxx.pdf
     */
    private String resolveObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BizException("简历文件地址为空");
        }

        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        String urlPrefix = endpoint + "/" + minioProperties.getBucketName() + "/";
        if (fileUrl.startsWith(urlPrefix)) {
            return fileUrl.substring(urlPrefix.length());
        }

        // 1. 兼容以后如果只保存 objectName 的情况。
        if (fileUrl.startsWith("resume/")) {
            return fileUrl;
        }
        throw new BizException("简历文件地址格式不正确");
    }

    /**
     * 获取文件扩展名。
     *
     * @param originalFilename 原始文件名
     * @return 返回小写扩展名，例如 .pdf；没有扩展名时返回空字符串
     */
    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 获取安全的原始文件名。
     *
     * @param originalFilename 浏览器上传的原始文件名
     * @return 返回去掉路径信息后的文件名
     */
    private String normalizeOriginalFilename(String originalFilename) {
        String filename = StringUtils.getFilename(originalFilename);
        if (!StringUtils.hasText(filename)) {
            return "resume";
        }
        return filename;
    }

    /**
     * 获取文件 Content-Type。
     *
     * @param file 前端上传的文件
     * @return 返回文件 Content-Type，拿不到时使用二进制兜底类型
     */
    private String resolveContentType(MultipartFile file) {
        if (!StringUtils.hasText(file.getContentType())) {
            return "application/octet-stream";
        }
        return file.getContentType();
    }

    /**
     * 字符串清洗工具。
     *
     * @param value 原始字符串
     * @return 去掉首尾空格后的字符串；如果没有有效内容则返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 打印简历上传失败的详细诊断信息。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @param originalFilename 原始文件名
     * @param exception 原始异常
     */
    private void printResumeUploadError(Long userId, String resumeName, String originalFilename, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 简历上传异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobResumeServiceImpl.uploadResume");
        System.err.println("用户ID：" + userId);
        System.err.println("简历名称：" + resumeName);
        System.err.println("原始文件名：" + originalFilename);
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }

    /**
     * 打印简历文件读取失败的详细诊断信息。
     *
     * @param resume 简历实体
     * @param exception 原始异常
     */
    private void printResumeFileError(JobResume resume, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 简历文件读取异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobResumeServiceImpl.openResumeFile");
        System.err.println("简历ID：" + resume.getId());
        System.err.println("用户ID：" + resume.getUserId());
        System.err.println("简历名称：" + resume.getResumeName());
        System.err.println("文件地址：" + resume.getFileUrl());
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }
}
