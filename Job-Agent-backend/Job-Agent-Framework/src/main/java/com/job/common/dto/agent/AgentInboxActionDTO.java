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
     * 稍后提醒时间。仅稍后提醒接口使用。
     */
    private Date snoozeUntil;
}
