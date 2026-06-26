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
}
