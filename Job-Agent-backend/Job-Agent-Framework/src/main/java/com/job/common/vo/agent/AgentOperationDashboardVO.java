package com.job.common.vo.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin Agent 运营看板数据。
 */
@Data
public class AgentOperationDashboardVO {

    private List<AgentOperationMetricVO> metrics = new ArrayList<>();

    private List<AgentOperationStatVO> reportStats = new ArrayList<>();

    private List<AgentOperationStatVO> actionStatusStats = new ArrayList<>();

    private List<AgentOperationStatVO> actionSourceStats = new ArrayList<>();

    private List<AgentOperationStatVO> actionTypeFailureStats = new ArrayList<>();

    private List<AgentOperationFailureVO> recentFailures = new ArrayList<>();
}
