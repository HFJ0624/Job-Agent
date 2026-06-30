package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Agent 每日求职日报记录。
 *
 * 说明：
 * 1. 这张表保存“某个用户某一天生成过什么日报”，避免每天只靠邮件发送后无法追溯。
 * 2. 第一版日报正文来自 Agent Inbox 的规则聚合，不调用大模型，先保证稳定、低成本、可查看。
 * 3. 邮件状态单独落库，方便后续做失败重试、Admin 统计和用户侧展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_daily_report_record")
public class AgentDailyReportRecord extends BaseEntity {

    /**
     * 日报所属用户 ID。
     */
    private Long userId;

    /**
     * 日报日期。按天去重，避免定时任务重复执行时生成多份。
     */
    private Date reportDate;

    /**
     * 日报标题。
     */
    private String reportTitle;

    /**
     * 页面列表和邮件顶部展示的摘要。
     */
    private String summaryText;

    /**
     * 纯文本日报正文，邮件第一版直接发送这个字段。
     */
    private String contentText;

    /**
     * 结构化日报内容 JSON，后续可用于更丰富的前端展示。
     */
    private String contentJson;

    /**
     * Inbox 待办总数快照。
     */
    private Integer inboxTotalCount;

    /**
     * 高优先级待办数快照。
     */
    private Integer highPriorityCount;

    /**
     * 已到期或今日到期待办数快照。
     */
    private Integer dueCount;

    /**
     * 实际收件邮箱。
     */
    private String emailTo;

    /**
     * 邮件状态：PENDING / SENT / SKIPPED / FAILED。
     */
    private String emailStatus;

    /**
     * 邮件发送失败原因。成功或跳过时为空。
     */
    private String emailError;

    /**
     * 邮件发送成功时间。
     */
    private Date sendTime;
}
