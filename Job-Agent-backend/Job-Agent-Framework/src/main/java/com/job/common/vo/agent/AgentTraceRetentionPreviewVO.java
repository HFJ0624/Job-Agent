package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent Trace 保留策略预览 VO
 * 日期: 2026/6/22
 */
@Data
@Builder
public class AgentTraceRetentionPreviewVO {

    private Long policyId;

    private String targetTable;

    private Integer retentionDays;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cutoffTime;

    private Long matchedCount;

    private Integer batchSize;
}
