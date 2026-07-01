package com.job.common.vo.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 运营分组统计项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentOperationStatVO {

    private String name;

    private Long count;

    private Double ratio;
}
