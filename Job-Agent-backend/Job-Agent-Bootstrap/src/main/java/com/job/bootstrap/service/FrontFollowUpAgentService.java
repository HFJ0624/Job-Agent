package com.job.bootstrap.service;

import com.job.common.vo.agent.FrontFollowUpCenterVO;

/**
 * 用户端求职跟进 Agent 聚合服务。
 */
public interface FrontFollowUpAgentService {

    FrontFollowUpCenterVO getCenter(Long userId);
}
