package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentInboxVO;

/**
 * 用户端 Agent Inbox 服务。
 */
public interface AgentInboxService {

    /**
     * 获取当前用户今日待处理事项。
     */
    AgentInboxVO getTodayInbox(Long userId);
}
