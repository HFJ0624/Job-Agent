package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentMemoryVO;

import java.util.List;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆捕获服务
 * 日期: 2026/6/23
 */
public interface AgentMemoryCaptureService {

    /**
     * 从用户本轮自然语言输入中捕获可长期保存的记忆。
     *
     * @param userId 当前用户 ID
     * @param conversationId 当前会话 ID，用作记忆来源 ID
     * @param traceId 当前链路 ID，便于后续接入观测
     * @param message 已经过 Guardrails 脱敏后的用户输入
     * @return 本轮写入或更新的记忆列表
     */
    List<AgentMemoryVO> captureFromUserMessage(Long userId, Long conversationId, String traceId, String message);
}
