package com.job.bootstrap.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件对象存储服务接口。
 *
 * <p>核心职责：为全站提供统一的文件上传、存储路径管理和 URL 生成能力，屏蔽底层对象存储实现差异。</p>
 *
 * <p>所属业务模块：基础设施 - 文件存储</p>
 *
 * <p>主要调用链：
 * ResumeController / PositionController / AvatarController -&gt; FileStorageService -&gt; MinioFileStorageServiceImpl / OssFileStorageServiceImpl</p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>Controller 和业务服务不直接拼接 MinIO objectName，统一由本服务生成并上传。</li>
 *   <li>后续若从 MinIO 迁移至 OSS/COS，仅需替换实现类，上层无感。</li>
 * </ol>
 * </p>
 */
public interface FileStorageService {

    /**
     * 上传文件到指定业务目录。
     *
     * @param file 前端上传的文件
     * @param folder 业务目录，例如 avatar 或 mock-interview/audio
     * @return 文件上传结果
     */
    StoredFile upload(MultipartFile file, String folder);

    /**
     * 上传服务端生成的文件内容（任务 J3：简历导出等连接器的真实执行）。
     *
     * @param content      文件字节内容
     * @param filename     展示用文件名（含扩展名）
     * @param contentType  MIME 类型
     * @param folder       业务目录，例如 resume-export
     * @param objectKey    稳定对象键（建议 "{userId}/{resumeId}.md"）——相同键重复上传覆盖同一对象，
     *                     内容一致即天然幂等，重复导出请求不会产生垃圾文件
     * @return 文件上传结果
     */
    StoredFile uploadBytes(byte[] content, String filename, String contentType, String folder, String objectKey);

    /**
     * 文件上传后的元数据。
     */
    record StoredFile(
            String fileUrl,
            String objectName,
            String originalFilename,
            Long fileSize,
            String contentType
    ) {
    }
}
