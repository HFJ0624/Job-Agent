package com.job.bootstrap.service;

/**
 * 项目统一邮件发送服务。
 *
 * <p>核心职责：为系统各业务模块提供标准化的邮件发送能力，统一封装邮件通道、模板渲染、投递追踪与失败重试逻辑。</p>
 *
 * <p>所属业务模块：基础设施 / 消息通知</p>
 *
 * <p>主要调用链：各业务 Service → JobMailSenderService → 邮件通道适配器（SMTP / 邮件服务商 SDK）</p>
 */
public interface JobMailSenderService {

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件标题
     * @param content 邮件正文（纯文本格式）
     */
    void sendText(String to, String subject, String content);
}
