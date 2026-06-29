package com.job.common.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台首页看板聚合数据。
 */
@Data
public class AdminDashboardOverviewVO {

    private List<AdminDashboardMetricVO> metrics = new ArrayList<>();

    private List<AdminDashboardPendingItemVO> pendingItems = new ArrayList<>();

    private List<AdminDashboardSystemItemVO> systemItems = new ArrayList<>();

    /**
     * 求职跟进 Agent 看板数据。
     */
    private List<AdminFollowUpAgentItemVO> followUpAgentItems = new ArrayList<>();
}
