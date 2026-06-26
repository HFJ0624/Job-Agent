package com.job.bootstrap.service;

import com.job.common.vo.admin.AdminDashboardOverviewVO;

/**
 * 后台首页看板聚合服务。
 */
public interface AdminDashboardService {

    /**
     * 查询后台首页真实数据库统计数据。
     *
     * @return 后台首页看板数据
     */
    AdminDashboardOverviewVO getOverview();
}
