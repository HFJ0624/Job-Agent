package com.job.common.dto.agent;

import lombok.Data;

import java.util.Date;

/**
 * Agent Inbox 待办操作请求。
 */
@Data
public class AgentInboxActionDTO {

    /**
     * 用户备注，可选。
     */
    private String note;

    /**
     * 原业务状态。
     *
     * 使用场景：
     * 1. 错题待办完成时，可以传 MASTERED 或 REVIEWING。
     * 2. 其他类型暂时不需要该字段。
     */
    private String businessStatus;

    /**
     * 稍后提醒时间。仅稍后提醒接口使用。
     */
    private Date snoozeUntil;
}
