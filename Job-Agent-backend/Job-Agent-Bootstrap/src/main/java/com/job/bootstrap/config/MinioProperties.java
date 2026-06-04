package com.job.bootstrap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 作者:hfj
 * 功能:MinIO 对象存储配置属性
 * 日期:2026/6/2 15:20
 */
@Data
@ConfigurationProperties(prefix = "job.minio")
public class MinioProperties {

    /**
     * MinIO 服务地址，例如 http://192.168.10.100:9001。
     */
    private String endpointUrl;

    /**
     * MinIO 访问账号。
     */
    private String accessKey;

    /**
     * MinIO 访问密钥。
     * P表示参数描述，这里沿用 application-dev.yml 中的 secreKey 写法。
     */
    private String secreKey;

    /**
     * 存储桶名称。
     */
    private String bucketName;
}
