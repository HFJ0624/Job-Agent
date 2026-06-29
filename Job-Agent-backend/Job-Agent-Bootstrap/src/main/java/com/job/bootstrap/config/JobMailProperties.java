package com.job.bootstrap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 功能：读取 application-local.yml 中的自定义邮箱配置。
 */
@Data
@ConfigurationProperties(prefix = "mail")
public class JobMailProperties {

    /**
     * 默认发件渠道，第一版默认使用 QQ 邮箱。
     */
    private String defaultProvider = "qq";

    /**
     * QQ 邮箱配置，对应 mail.qq。
     */
    private MailAccount qq = new MailAccount();

    /**
     * 163 邮箱配置，对应 mail.163。
     */
    private MailAccount mail163 = new MailAccount();

    /**
     * Spring Boot 绑定数字开头的属性名时不适合作为 Java 字段名，所以通过 setter 接收 mail.163。
     */
    public void set163(MailAccount mail163) {
        this.mail163 = mail163;
    }

    /**
     * 根据渠道名称选择邮箱账号。
     */
    public MailAccount account(String provider) {
        String safeProvider = provider == null || provider.isBlank() ? defaultProvider : provider.trim();
        if ("163".equalsIgnoreCase(safeProvider)) {
            return mail163;
        }
        return qq;
    }

    @Data
    public static class MailAccount {

        /**
         * SMTP 服务器地址，例如 smtp.qq.com。
         */
        private String host;

        /**
         * SMTP 端口，SSL 通常是 465。
         */
        private Integer port = 465;

        /**
         * 发件邮箱账号。
         */
        private String username;

        /**
         * 邮箱授权码，不建议使用登录密码。
         */
        private String password;

        /**
         * 邮件协议，默认 smtp。
         */
        private String protocol = "smtp";

        /**
         * 邮件编码，默认 UTF-8。
         */
        private String defaultEncoding = "UTF-8";

        /**
         * 兼容当前 application-local.yml 中的 properties.mail.smtp.* 嵌套结构。
         */
        private Map<String, Object> properties = new HashMap<>();
    }
}
