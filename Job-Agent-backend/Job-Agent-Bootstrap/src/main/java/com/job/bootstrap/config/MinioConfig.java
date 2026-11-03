package com.job.bootstrap.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:MinIO 客户端配置
 * 日期:2026/6/2 15:20
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private final MinioProperties minioProperties;

    /**
     * 创建 MinIO 客户端。
     *
     * @return 返回可执行上传、建桶等操作的 MinIO 客户端
     */
    @Bean
    public MinioClient minioClient() {
        String accessKey = minioProperties.getAccessKey();
        String secretKey = minioProperties.getSecreKey();

        /*
         * 未配置密钥时使用占位凭证，保证应用能启动：
         * 1. 本地开发经常只想验证 Agent/匹配等主流程，不应被对象存储阻断；
         * 2. 占位凭证下上传/下载会失败，日志已明确提示，避免"看似可用"的误解；
         * 3. 生产/需要上传时在环境变量 MINIO_ACCESS_KEY / MINIO_SECRET_KEY 或
         *    application-local.yml 的 job.minio.* 配置真实密钥。
         */
        boolean configured = accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
        if (!configured) {
            log.warn("MinIO 未配置密钥（job.minio.accessKey/secreKey 为空），"
                    + "应用仍可启动，但简历上传/文件读取不可用；请配置 MINIO_ACCESS_KEY / MINIO_SECRET_KEY");
            return MinioClient.builder()
                    .endpoint(minioProperties.getEndpointUrl())
                    .credentials("minio-not-configured", "minio-not-configured")
                    .build();
        }

        // 根据 yml 中的 MinIO 地址、账号和密钥创建客户端。
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpointUrl())
                .credentials(accessKey, secretKey)
                .build();
    }
}
