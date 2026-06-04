package com.job.bootstrap.controller.front;

import com.job.bootstrap.config.MinioProperties;
import com.job.common.vo.file.FileUploadVO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * 作者:hfj
 * 功能:文件上传接口，当前用于注册页头像上传
 * 日期:2026/6/2 15:20
 */
@RestController
@RequestMapping("/front/file")
@RequiredArgsConstructor
public class FileController {

    private static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;

    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    /**
     * 上传用户头像。
     * P表示参数描述，注册前也允许上传，所以这个接口在 SaTokenConfig 中放行。
     *
     * @param file 前端选择的头像文件
     * @return 返回头像访问地址和 MinIO 对象名
     */
    @PostMapping("/avatar")
    public Result<FileUploadVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验头像文件，避免空文件、超大文件或非图片文件上传。
            validateAvatar(file);

            // 2. 确保存储桶存在，不存在就自动创建。
            ensureBucketExists();

            // 3. 生成对象名并上传到 MinIO。
            String objectName = buildObjectName(file.getOriginalFilename());
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .contentType(file.getContentType())
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());

            // 4. 返回可保存到用户 avatarUrl 字段的访问地址。
            FileUploadVO response = new FileUploadVO(
                    buildFileUrl(objectName),
                    objectName,
                    file.getOriginalFilename()
            );
            return Result.build(response, ResultCodeEnum.SUCCESS);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "头像上传失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 校验头像文件。
     *
     * @param file 前端上传的文件
     */
    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择头像文件");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BizException("头像文件不能超过2MB");
        }
        if (!IMAGE_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BizException("头像只支持 JPG、PNG、WEBP、GIF 格式");
        }
    }

    /**
     * 确保存储桶存在。
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
     * 构造 MinIO 对象名。
     *
     * @param originalFilename 原始文件名
     * @return 返回头像对象名
     */
    private String buildObjectName(String originalFilename) {
        String extension = getExtension(originalFilename);
        LocalDate now = LocalDate.now();
        return "avatar/" + now.getYear() + "/" + now.getMonthValue() + "/" + UUID.randomUUID() + extension;
    }

    /**
     * 获取文件扩展名。
     *
     * @param originalFilename 原始文件名
     * @return 返回扩展名，拿不到时默认 .png
     */
    private String getExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return ".png";
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    /**
     * 构造文件访问地址。
     *
     * @param objectName MinIO 对象名
     * @return 返回文件访问 URL
     */
    private String buildFileUrl(String objectName) {
        String endpoint = minioProperties.getEndpointUrl();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }
}
