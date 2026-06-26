package com.job.bootstrap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 功能: ASR 配置注册。
 */
@Configuration
@EnableConfigurationProperties(VolcengineAsrProperties.class)
public class AsrConfig {
}
