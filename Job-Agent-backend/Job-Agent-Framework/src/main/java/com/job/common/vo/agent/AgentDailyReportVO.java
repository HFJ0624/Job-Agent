package com.job.common.vo.agent;

import lombok.Data;

import java.util.Date;

/**
 * 用户端 Agent 日报展示对象。
 *
 * 说明：
 * 1. VO 只暴露页面需要展示的字段，不把数据库实体直接返回给前端。
 * 2. 邮件失败原因会返回，方便用户知道“日报已生成但邮件没有发出去”的具体原因。
 */
@Data
public class AgentDailyReportVO {

    private Long id;

    private Long userId;

    private Date reportDate;

    private String reportTitle;

    private String summaryText;

    private String contentText;

    private String generationStatus;

    private String generationSource;

    private String generationError;

    private Integer inboxTotalCount;

    private Integer highPriorityCount;

    private Integer dueCount;

    private String emailTo;

    private String emailStatus;

    private String emailError;

    private Date sendTime;

    private Date createTime;
}
