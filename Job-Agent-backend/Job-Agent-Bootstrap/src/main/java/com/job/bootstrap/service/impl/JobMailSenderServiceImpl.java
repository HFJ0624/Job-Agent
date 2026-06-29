package com.job.bootstrap.service.impl;

import com.job.bootstrap.config.JobMailProperties;
import com.job.bootstrap.service.JobMailSenderService;
import com.job.exception.BizException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * 功能：基于 Spring Mail 的邮件发送实现。
 *
 * 实现步骤：
 * 1. 从 JobMailProperties 读取默认邮箱账号，第一版默认是 mail.qq。
 * 2. 将 application-local.yml 里的 host、port、username、password、ssl 等配置转换成 JavaMail 配置。
 * 3. 创建 MimeMessage 并发送纯文本邮件。
 * 4. 发送失败时抛出业务异常，让工作流任务队列接管重试和失败记录。
 */
@Service
@RequiredArgsConstructor
public class JobMailSenderServiceImpl implements JobMailSenderService {

    private final JobMailProperties mailProperties;

    @Override
    public void sendText(String to, String subject, String content) {
        if (!StringUtils.hasText(to)) {
            throw new BizException("收件人邮箱不能为空");
        }

        JobMailProperties.MailAccount account = mailProperties.account(null);
        validateAccount(account);

        try {
            JavaMailSenderImpl sender = buildSender(account);
            Charset charset = resolveCharset(account.getDefaultEncoding());

            /*
             * 1. 使用 MimeMessageHelper 明确指定编码，避免中文标题和正文乱码。
             */
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, charset.name());
            helper.setFrom(account.getUsername());
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(content, false);

            /*
             * 2. 真正发送由 Spring Mail 完成；如果 SMTP 账号、授权码或网络异常，会抛出异常。
             */
            sender.send(message);
        } catch (Exception exception) {
            throw new BizException("邮件发送失败：" + exception.getMessage());
        }
    }

    private JavaMailSenderImpl buildSender(JobMailProperties.MailAccount account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(account.getPassword());
        sender.setProtocol(StringUtils.hasText(account.getProtocol()) ? account.getProtocol() : "smtp");
        sender.setDefaultEncoding(resolveCharset(account.getDefaultEncoding()).name());
        sender.setJavaMailProperties(toJavaMailProperties(account.getProperties()));
        return sender;
    }

    private Properties toJavaMailProperties(Map<String, Object> source) {
        Properties properties = new Properties();

        /*
         * 1. 当前 yml 是 properties.mail.smtp.auth 这种嵌套结构。
         * 2. JavaMail 需要 mail.smtp.auth 这种平铺 key，所以这里递归压平。
         */
        flatten("", source, properties);

        /*
         * 3. 设置基础超时，避免 SMTP 长时间无响应导致工作流线程卡住。
         */
        properties.putIfAbsent("mail.smtp.connectiontimeout", "10000");
        properties.putIfAbsent("mail.smtp.timeout", "10000");
        properties.putIfAbsent("mail.smtp.writetimeout", "10000");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Properties target) {
        if (source == null || source.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flatten(key, (Map<String, Object>) nested, target);
            } else if (value != null) {
                target.put(key, String.valueOf(value));
            }
        }
    }

    private Charset resolveCharset(String charsetName) {
        if (!StringUtils.hasText(charsetName)) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(charsetName.trim());
    }

    private void validateAccount(JobMailProperties.MailAccount account) {
        if (account == null
                || !StringUtils.hasText(account.getHost())
                || account.getPort() == null
                || !StringUtils.hasText(account.getUsername())
                || !StringUtils.hasText(account.getPassword())) {
            throw new BizException("邮箱配置不完整，请检查 application-local.yml 的 mail.qq 配置");
        }
    }
}
