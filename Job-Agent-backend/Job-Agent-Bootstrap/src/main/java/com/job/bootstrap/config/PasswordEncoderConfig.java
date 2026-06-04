package com.job.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 作者:hfj
 * 功能:密码加密器配置
 * 日期:2026/6/2 10:45
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * 创建 BCrypt 密码加密器。
     *
     * @return 返回密码加密和密码校验工具
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 1. BCrypt 会自动加盐，比直接存明文密码更安全。
        return new BCryptPasswordEncoder();
    }
}
