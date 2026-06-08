package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 作者:hfj
 * 功能:Agent 调用链路日志
 * 日期: 2026/6/8 15:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_trace_log")
public class AgentTraceLog extends BaseEntity {

    private String traceId;

    private Long userId;

    private Long conversationId;

    private String intentCode;

    private String toolName;

    private String inputData;

    private String outputData;

    private String status;

    private String errorMsg;

    private Long costTime;
}
