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
 * 基于 Spring Mail 的邮件发送服务实现。
 *
 * <p>核心职责：读取应用配置文件中的邮箱账号信息，构建 JavaMailSender 并发送纯文本邮件。
 * 发送失败时统一抛出业务异常，由上层工作流任务队列接管重试和失败记录。</p>
 *
 * <p>所属业务模块：系统基础设施 - 邮件通知服务</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>业务服务 / Agent 任务调用 {@link #sendText}</li>
 *   <li>从 {@link JobMailProperties} 读取默认账号配置</li>
 *   <li>构建 {@link JavaMailSenderImpl} 并发送 {@link MimeMessage}</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link JobMailProperties}：承载 application-local.yml 中的多账号邮件配置</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>从 JobMailProperties 读取默认邮箱账号，第一版默认是 mail.qq。</li>
 *   <li>将 application-local.yml 里的 host、port、username、password、ssl 等配置转换成 JavaMail 配置。</li>
 *   <li>创建 MimeMessage 并发送纯文本邮件。</li>
 *   <li>发送失败时抛出业务异常，让工作流任务队列接管重试和失败记录。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class JobMailSenderServiceImpl implements JobMailSenderService {

    private final JobMailProperties mailProperties;

    /**
     * 发送纯文本邮件。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验收件人邮箱非空。</li>
     *   <li>读取默认邮件账号配置并校验完整性。</li>
     *   <li>构建 {@link JavaMailSenderImpl} 和 {@link MimeMessageHelper}，显式指定编码避免中文乱码。</li>
     *   <li>执行发送，异常时转为业务异常抛出。</li>
     * </ol>
     * </p>
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文（纯文本）
     * @throws BizException 收件人邮箱为空、账号配置不完整或 SMTP 发送失败
     */
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

    /**
     * 根据账号配置构建 JavaMailSenderImpl。
     *
     * @param account 邮件账号配置
     * @return 已配置完成的邮件发送器
     */
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

    /**
     * 将嵌套 Map 配置压平为 JavaMail 所需的 Properties。
     *
     * <p>yml 中 properties.mail.smtp.auth 这种嵌套结构会被递归压平为 mail.smtp.auth。
     * 同时设置默认 SMTP 超时，避免工作流线程因网络阻塞长时间挂起。</p>
     *
     * @param source 嵌套配置 Map
     * @return 压平后的 JavaMail Properties
     */
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

    /**
     * 递归压平嵌套 Map，将其转换为 Properties 的平铺键值对。
     *
     * @param prefix 当前层级前缀
     * @param source 源嵌套 Map
     * @param target 目标 Properties
     */
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

    /**
     * 解析字符集，空值时默认返回 UTF-8。
     *
     * @param charsetName 字符集名称
     * @return 对应的 {@link Charset} 实例
     */
    private Charset resolveCharset(String charsetName) {
        if (!StringUtils.hasText(charsetName)) {
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(charsetName.trim());
    }

    /**
     * 校验邮件账号配置完整性。
     *
     * @param account 邮件账号配置
     * @throws BizException 配置不完整时抛出
     */
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
