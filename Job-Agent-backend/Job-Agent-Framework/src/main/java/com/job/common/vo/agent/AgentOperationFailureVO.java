package com.job.common.vo.agent;

import lombok.Data;

import java.util.Date;

/**
 * Agent 运营失败记录。
 */
@Data
public class AgentOperationFailureVO {

    private String failureType;

    private Long userId;

    private String title;

    private String reason;

    private Date createTime;
}
