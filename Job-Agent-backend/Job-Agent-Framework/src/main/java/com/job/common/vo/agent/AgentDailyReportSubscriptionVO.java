package com.job.common.vo.agent;

import lombok.Data;

import java.util.Date;

/**
 * Agent 日报订阅配置展示对象。
 */
@Data
public class AgentDailyReportSubscriptionVO {

    private Long id;

    private Long userId;

    private Integer enabled;

    private String sendTime;

    private Integer emailEnabled;

    private Date lastGenerateDate;

    private Date updateTime;
}
