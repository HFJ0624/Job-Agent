package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentMemoryVO;

import java.util.List;

/**
 * Agent 长期记忆捕获服务接口。
 *
 * <p>核心职责：从用户自然语言输入中实时提取关键信息（如求职意向、技能变更、偏好调整），并转化为结构化长期记忆。</p>
 *
 * <p>所属业务模块：AI 助手 - 长期记忆（Long-Term Memory）</p>
 *
 * <p>主要调用链：
 * AgentChatService -&gt; AgentMemoryCaptureService -&gt; AgentMemoryCaptureServiceImpl -&gt; AiModelGatewayService / AgentMemoryService</p>
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
