package com.job.bootstrap.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:MinIO 客户端配置
 * 日期:2026/6/2 15:20
 */
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
        // 1. 根据 yml 中的 MinIO 地址、账号和密钥创建客户端。
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpointUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecreKey())
                .build();
    }
}
