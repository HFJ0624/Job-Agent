package com.job.bootstrap.service.impl;

import com.job.bootstrap.config.MinioProperties;
import com.job.bootstrap.service.FileStorageService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 功能: MinIO 文件存储实现。
 */
@Service
@RequiredArgsConstructor
public class MinioFileStorageServiceImpl implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public StoredFile upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }

        try {
            // 1. 先确保桶存在，避免新环境第一次上传时报 bucket not found。
            ensureBucketExists();

            // 2. 按业务目录 + 年/月分层，避免单目录对象过多。
            String objectName = buildObjectName(folder, file.getOriginalFilename());

            // 3. 将浏览器上传流直接写入 MinIO，contentType 原样保存便于后续播放。
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .contentType(file.getContentType())
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());

            // 4. 返回业务需要持久化的文件元数据。
            return new StoredFile(
                    buildFileUrl(objectName),
                    objectName,
                    file.getOriginalFilename(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "文件上传失败: " + exception.getMessage(), exception);
        }
    }

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

    private String buildObjectName(String folder, String originalFilename) {
        String cleanFolder = StringUtils.hasText(folder) ? folder.trim() : "common";
        LocalDate now = LocalDate.now();
        return cleanFolder + "/" + now.getYear() + "/" + now.getMonthValue()
                + "/" + UUID.randomUUID() + getExtension(originalFilename);
    }

    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    private String buildFileUrl(String objectName) {
        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }
}
