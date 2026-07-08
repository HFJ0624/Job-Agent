package com.job.bootstrap.service;

import com.job.common.vo.admin.AdminDashboardOverviewVO;

/**
 * 后台首页看板聚合服务。
 *
 * <p>核心职责：为管理员后台首页提供核心业务数据聚合与统计能力，整合用户、求职、面试、Agent 等多维度实时数据指标。</p>
 *
 * <p>所属业务模块：后台管理 / 数据看板</p>
 *
 * <p>主要调用链：Admin Controller → AdminDashboardService → 各业务领域统计 Service / Mapper / 缓存层</p>
 */
public interface AdminDashboardService {

    /**
     * 查询后台首页看板聚合统计数据。
     *
     * @return 后台首页看板数据，包含用户统计、求职统计、面试统计、Agent 运营指标等多维度数据
     */
    AdminDashboardOverviewVO getOverview();
}
