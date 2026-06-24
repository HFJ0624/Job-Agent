package com.job.bootstrap.service;

import com.job.common.vo.home.HomeOverviewVO;

/**
 * 作者:hfj
 * 功能:用户端首页聚合服务，负责把多个业务模块的数据整理成首页一次性可用的数据
 * 日期:2026/6/24
 */
public interface FrontHomeService {

    /**
     * 查询用户端首页真实数据。
     *
     * @param userId 当前登录用户ID
     * @return 首页聚合数据
     */
    HomeOverviewVO getOverview(Long userId);
}
