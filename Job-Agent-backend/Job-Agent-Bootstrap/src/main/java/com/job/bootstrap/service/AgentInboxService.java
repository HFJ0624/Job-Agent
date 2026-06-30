package com.job.bootstrap.service;

import com.job.common.dto.agent.AgentInboxActionDTO;
import com.job.common.vo.agent.AgentInboxVO;

/**
 * 用户端 Agent Inbox 服务。
 */
public interface AgentInboxService {

    /**
     * 获取当前用户今日待处理事项。
     */
    AgentInboxVO getTodayInbox(Long userId);

    /**
     * 标记某条 Inbox 待办完成。
     */
    void markDone(Long userId, String itemKey, AgentInboxActionDTO dto);

    /**
     * 忽略某条 Inbox 待办。
     */
    void ignore(Long userId, String itemKey, AgentInboxActionDTO dto);

    /**
     * 稍后提醒某条 Inbox 待办。
     */
    void snooze(Long userId, String itemKey, AgentInboxActionDTO dto);
}
