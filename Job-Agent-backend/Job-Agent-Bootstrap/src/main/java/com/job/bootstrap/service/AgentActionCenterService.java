package com.job.bootstrap.service;

import com.job.common.dto.agent.AgentActionItemStatusDTO;
import com.job.common.vo.agent.AgentActionItemVO;

import java.util.List;

/**
 * Agent 行动确认中心服务。
 *
 * <p>核心职责：为用户提供 Agent 生成行动项的确认与处理能力，用户可对待办行动项执行完成、忽略、稍后提醒等操作。</p>
 *
 * <p>所属业务模块：Agent 行动中心 / 用户端</p>
 *
 * <p>主要调用链：Front Controller → AgentActionCenterService → AgentActionItem 领域 Service / Mapper</p>
 */
public interface AgentActionCenterService {

    /**
     * 查询当前用户待确认的行动项列表。
     *
     * @param userId 当前用户 ID
     * @param limit  查询条数上限
     * @return 待确认行动项列表
     */
    List<AgentActionItemVO> listPending(Long userId, int limit);

    /**
     * 用户标记指定行动项已完成。
     *
     * @param userId   当前用户 ID
     * @param actionId 行动项 ID
     * @param dto      状态变更参数（包含备注、完成方式等业务信息）
     */
    void markDone(Long userId, Long actionId, AgentActionItemStatusDTO dto);

    /**
     * 用户忽略指定行动项。
     *
     * @param userId   当前用户 ID
     * @param actionId 行动项 ID
     * @param dto      状态变更参数（包含忽略原因等业务信息）
     */
    void ignore(Long userId, Long actionId, AgentActionItemStatusDTO dto);

    /**
     * 用户将指定行动项设置为稍后处理。
     *
     * @param userId   当前用户 ID
     * @param actionId 行动项 ID
     * @param dto      状态变更参数（包含提醒时间等业务信息）
     */
    void snooze(Long userId, Long actionId, AgentActionItemStatusDTO dto);
}
