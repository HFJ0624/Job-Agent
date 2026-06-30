package com.job.common.vo.agent;

import lombok.Data;

import java.util.Date;

/**
 * Agent 行动确认项展示对象。
 */
@Data
public class AgentActionItemVO {

    private Long id;

    private String actionKey;

    private String sourceType;

    private Long sourceId;

    private String actionType;

    private String actionTitle;

    private String actionDesc;

    private String priority;

    private String actionStatus;

    private String targetPath;

    private Date snoozeUntil;

    private String note;

    private Date doneTime;

    private Date createTime;

    private Date updateTime;
}
