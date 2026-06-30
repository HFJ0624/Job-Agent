package com.job.common.dto.agent;

import lombok.Data;

/**
 * Agent 日报订阅保存参数。
 */
@Data
public class AgentDailyReportSubscriptionSaveDTO {

    /**
     * 是否启用日报：1 启用，0 关闭。
     */
    private Integer enabled;

    /**
     * 发送时间，格式 HH:mm。
     */
    private String sendTime;

    /**
     * 是否发送邮件：1 发送，0 只生成站内日报。
     */
    private Integer emailEnabled;
}
