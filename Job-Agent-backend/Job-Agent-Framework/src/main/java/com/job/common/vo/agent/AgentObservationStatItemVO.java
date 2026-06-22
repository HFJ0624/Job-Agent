package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 观测统计项 VO
 * 日期: 2026/6/22
 */
@Data
@Builder
public class AgentObservationStatItemVO {

    private String name;

    private Long count;

    private BigDecimal ratio;

    private BigDecimal totalCost;

    private Long totalTokens;

    private Long avgDurationMs;

    private Long maxDurationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastTime;
}
