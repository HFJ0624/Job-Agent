package com.job.bootstrap.service;

/**
 * 功能：项目统一邮件发送服务。
 */
public interface JobMailSenderService {

    /**
     * 发送纯文本邮件。
     *
     * @param to 收件人邮箱
     * @param subject 邮件标题
     * @param content 邮件正文
     */
    void sendText(String to, String subject, String content);
}
