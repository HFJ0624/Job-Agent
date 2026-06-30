package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Agent 主动日报订阅配置。
 *
 * 说明：
 * 1. 每个用户最多一条配置，用来控制是否接收日报、几点接收、是否发送邮件。
 * 2. 定时任务按 sendTime 扫描，避免所有用户都固定 9 点发送。
 * 3. lastGenerateDate 用于同一天去重，防止每小时扫描时重复生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_daily_report_subscription")
public class AgentDailyReportSubscription extends BaseEntity {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 是否启用日报：1 启用，0 关闭。
     */
    private Integer enabled;

    /**
     * 用户希望接收日报的时间，格式 HH:mm。
     */
    private String sendTime;

    /**
     * 是否发送邮件：1 发送，0 只生成站内日报。
     */
    private Integer emailEnabled;

    /**
     * 上次成功触发生成的日期，用于定时任务按天去重。
     */
    private Date lastGenerateDate;
}
