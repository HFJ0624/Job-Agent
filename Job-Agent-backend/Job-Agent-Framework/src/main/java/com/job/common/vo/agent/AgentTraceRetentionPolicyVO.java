package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentTraceRetentionPolicy;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent Trace 保留策略 VO
 * 日期: 2026/6/22
 */
@Data
public class AgentTraceRetentionPolicyVO {

    private Long id;

    private String policyName;

    private String targetTable;

    private Integer retentionDays;

    private Integer batchSize;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastExecuteTime;

    private Integer lastDeletedCount;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentTraceRetentionPolicyVO from(AgentTraceRetentionPolicy entity) {
        if (entity == null) {
            return null;
        }

        AgentTraceRetentionPolicyVO vo = new AgentTraceRetentionPolicyVO();
        vo.setId(entity.getId());
        vo.setPolicyName(entity.getPolicyName());
        vo.setTargetTable(entity.getTargetTable());
        vo.setRetentionDays(entity.getRetentionDays());
        vo.setBatchSize(entity.getBatchSize());
        vo.setStatus(entity.getStatus());
        vo.setLastExecuteTime(entity.getLastExecuteTime());
        vo.setLastDeletedCount(entity.getLastDeletedCount());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
