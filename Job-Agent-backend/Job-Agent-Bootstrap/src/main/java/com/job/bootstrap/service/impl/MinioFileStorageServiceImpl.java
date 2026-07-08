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
 * MinIO 文件存储实现。
 *
 * <p>核心职责：提供基于 MinIO 的对象存储能力，包括文件上传、桶自动创建和访问 URL 生成。</p>
 *
 * <p>所属业务模块：基础设施模块（infrastructure）/ 文件存储模块（storage）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>业务模块调用 {@link #upload} 上传文件；</li>
 *   <li>内部自动确保桶存在，并按业务目录 + 年/月分层生成对象名；</li>
 *   <li>返回包含访问 URL 和对象名的 StoredFile，供业务持久化。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link MinioClient} 与 MinIO 服务交互；</li>
 *   <li>依赖 {@link MinioProperties} 获取桶名、 endpoint 等配置。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>上传前自动检查并创建桶，避免新环境首次上传报错；</li>
 *   <li>对象名按 folder/年/月/UUID.扩展名 分层，避免单目录对象过多；</li>
 *   <li>contentType 原样保存，便于后续浏览器直接预览或播放。</li>
 * </ol>
 * </p>
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

    /**
     * 检查并自动创建存储桶，避免首次上传时报 bucket not found。
     *
     * @throws Exception MinIO 操作异常
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
     * 构建对象名，按业务目录 + 年/月分层，避免单目录对象过多。
     *
     * @param folder           业务目录
     * @param originalFilename 原始文件名
     * @return 对象全路径
     */
    private String buildObjectName(String folder, String originalFilename) {
        String cleanFolder = StringUtils.hasText(folder) ? folder.trim() : "common";
        LocalDate now = LocalDate.now();
        return cleanFolder + "/" + now.getYear() + "/" + now.getMonthValue()
                + "/" + UUID.randomUUID() + getExtension(originalFilename);
    }

    /**
     * 提取文件扩展名并转为小写。
     *
     * @param originalFilename 原始文件名
     * @return 扩展名（含点号），无扩展名返回空字符串
     */
    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 构建文件访问 URL。
     *
     * @param objectName 对象名
     * @return 完整访问 URL
     */
    private String buildFileUrl(String objectName) {
        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }
}
