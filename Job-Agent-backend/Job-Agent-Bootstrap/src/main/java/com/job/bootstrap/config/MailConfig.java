package com.job.bootstrap.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 功能：注册项目自定义邮箱配置。
 *
 * 说明：
 * 1. 当前项目的本地配置文件使用 mail.qq / mail.163，而不是 Spring Boot 默认的 spring.mail。
 * 2. 这里仅负责让 JobMailProperties 生效，真正的发送逻辑放到统一邮件服务里。
 */
@Configuration
@EnableConfigurationProperties(JobMailProperties.class)
public class MailConfig {
}
