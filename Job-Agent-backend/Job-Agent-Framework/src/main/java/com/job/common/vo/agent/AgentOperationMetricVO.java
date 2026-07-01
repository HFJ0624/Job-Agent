package com.job.common.vo.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 运营指标卡。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentOperationMetricVO {

    private String label;

    private Long value;

    private String subText;

    private String level;
}
