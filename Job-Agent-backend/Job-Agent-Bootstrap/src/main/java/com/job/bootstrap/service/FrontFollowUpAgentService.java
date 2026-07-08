package com.job.bootstrap.service;

import com.job.common.vo.agent.FrontFollowUpCenterVO;

/**
 * 用户端求职跟进 Agent 聚合服务。
 *
 * <p>核心职责：为用户提供求职跟进中心的聚合数据查询，整合各阶段求职申请的跟进状态、待办事项、关键提醒等信息。</p>
 *
 * <p>所属业务模块：求职跟进 / 用户端聚合服务</p>
 *
 * <p>主要调用链：Front Controller → FrontFollowUpAgentService → 求职申请 Service / 跟进规则 Service / Agent 行动项 Service</p>
 */
public interface FrontFollowUpAgentService {

    /**
     * 查询当前用户的求职跟进中心聚合数据。
     *
     * @param userId 当前用户 ID
     * @return 求职跟进中心数据，包含申请列表、阶段分布、待办提醒、关键节点等
     */
    FrontFollowUpCenterVO getCenter(Long userId);
}
