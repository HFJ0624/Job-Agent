package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent Trace 保留策略实体
 * 日期: 2026/6/22
 *
 * 说明:
 * 1. 第一版只做逻辑删除，不做物理删除，避免误删排障数据。
 * 2. targetTable 只允许后端白名单内的日志表，不能由前端任意传表名执行 SQL。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_trace_retention_policy")
public class AgentTraceRetentionPolicy extends BaseEntity {

    private String policyName;

    private String targetTable;

    private Integer retentionDays;

    private Integer batchSize;

    private String status;

    private Date lastExecuteTime;

    private Integer lastDeletedCount;

    private String remark;
}
