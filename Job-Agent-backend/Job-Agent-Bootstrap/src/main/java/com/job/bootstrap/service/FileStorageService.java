package com.job.bootstrap.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 功能: 文件对象存储服务。
 *
 * 说明:
 * 1. Controller 和业务服务不直接拼 MinIO objectName，统一从这里上传。
 * 2. 后续如果从 MinIO 换成 OSS/COS，只需要替换这个实现。
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
