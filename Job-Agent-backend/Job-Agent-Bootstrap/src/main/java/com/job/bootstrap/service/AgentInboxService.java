package com.job.bootstrap.service;

import com.job.common.dto.agent.AgentInboxActionDTO;
import com.job.common.vo.agent.AgentInboxVO;

/**
 * 用户端 Agent Inbox 服务。
 *
 * <p>核心职责：为用户提供 Agent 生成的每日待办收件箱，聚合求职过程中的关键待处理事项，支持用户对事项进行完成、忽略、稍后提醒等操作。</p>
 *
 * <p>所属业务模块：Agent Inbox / 用户端待办中心</p>
 *
 * <p>主要调用链：Front Controller → AgentInboxService → Inbox 领域 Service / Mapper / 各业务领域事件源</p>
 */
public interface AgentInboxService {

    /**
     * 获取当前用户今日待处理事项收件箱。
     *
     * @param userId 当前用户 ID
     * @return 今日 Inbox 聚合数据，包含待办列表、分类统计、优先级标识
     */
    AgentInboxVO getTodayInbox(Long userId);

    /**
     * 标记某条 Inbox 待办事项已完成。
     *
     * @param userId  当前用户 ID
     * @param itemKey 待办事项唯一标识
     * @param dto     操作参数（包含完成备注、关联业务动作等）
     */
    void markDone(Long userId, String itemKey, AgentInboxActionDTO dto);

    /**
     * 忽略某条 Inbox 待办事项。
     *
     * @param userId  当前用户 ID
     * @param itemKey 待办事项唯一标识
     * @param dto     操作参数（包含忽略原因等）
     */
    void ignore(Long userId, String itemKey, AgentInboxActionDTO dto);

    /**
     * 将某条 Inbox 待办事项设置为稍后提醒。
     *
     * @param userId  当前用户 ID
     * @param itemKey 待办事项唯一标识
     * @param dto     操作参数（包含提醒时间、推迟原因等）
     */
    void snooze(Long userId, String itemKey, AgentInboxActionDTO dto);
}
