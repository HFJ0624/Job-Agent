package com.job.bootstrap.service;

import com.job.common.dto.agent.AgentActionItemStatusDTO;
import com.job.common.vo.agent.AgentActionItemVO;

import java.util.List;

/**
 * Agent 行动确认中心服务。
 */
public interface AgentActionCenterService {

    /**
     * 查询当前用户待确认行动项。
     */
    List<AgentActionItemVO> listPending(Long userId, int limit);

    /**
     * 标记完成。
     */
    void markDone(Long userId, Long actionId, AgentActionItemStatusDTO dto);

    /**
     * 忽略行动项。
     */
    void ignore(Long userId, Long actionId, AgentActionItemStatusDTO dto);

    /**
     * 稍后处理行动项。
     */
    void snooze(Long userId, Long actionId, AgentActionItemStatusDTO dto);
}
