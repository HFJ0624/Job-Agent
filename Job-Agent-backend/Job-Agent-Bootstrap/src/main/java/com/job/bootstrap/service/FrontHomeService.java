package com.job.bootstrap.service;

import com.job.common.vo.home.HomeOverviewVO;

/**
 * 用户端首页聚合服务接口。
 *
 * <p>核心职责：整合求职进度、岗位推荐、面试安排、消息提醒等多模块数据，为首页提供一站式数据聚合。</p>
 *
 * <p>所属业务模块：用户端 - 首页门户</p>
 *
 * <p>主要调用链：
 * FrontHomeController -&gt; FrontHomeService -&gt; FrontHomeServiceImpl -&gt; JobApplicationService / JobReminderService / UserJobPreferenceService / JobPositionService</p>
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
