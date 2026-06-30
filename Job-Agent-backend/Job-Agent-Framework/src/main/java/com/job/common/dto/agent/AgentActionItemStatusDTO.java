package com.job.common.dto.agent;

import lombok.Data;

import java.util.Date;

/**
 * Agent 行动项状态更新参数。
 */
@Data
public class AgentActionItemStatusDTO {

    /**
     * 稍后处理时间。只有 SNOOZED 状态需要。
     */
    private Date snoozeUntil;

    /**
     * 用户备注。
     */
    private String note;
}
