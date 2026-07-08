package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentMemoryContextVO;
import com.job.common.vo.agent.AgentUserMemoryProfileVO;

/**
 * Agent 长期记忆上下文服务接口。
 *
 * <p>核心职责：基于用户当前问题检索相关长期记忆，并构造符合 Token 预算的 Prompt 上下文片段，同时维护用户画像摘要。</p>
 *
 * <p>所属业务模块：AI 助手 - 长期记忆（Long-Term Memory）</p>
 *
 * <p>主要调用链：
 * AgentPlanningService / AgentChatService -&gt; AgentMemoryContextService -&gt; AgentMemoryContextServiceImpl -&gt; AgentMemoryService / VectorStore</p>
 */
public interface AgentMemoryContextService {

    /**
     * 为本轮用户问题构造可注入 Prompt 的长期记忆上下文。
     *
     * @param userId 当前用户 ID
     * @param query 当前问题或 Planner 检索词
     * @return 已做 token 预算裁剪的记忆上下文
     */
    AgentMemoryContextVO buildContext(Long userId, String query);

    /**
     * 查询用户画像摘要。
     *
     * @param userId 用户 ID
     * @return 用户画像摘要
     */
    AgentUserMemoryProfileVO getProfile(Long userId);

    /**
     * 基于当前有效长期记忆重建用户画像摘要。
     *
     * @param userId 用户 ID
     * @return 重建后的画像摘要
     */
    AgentUserMemoryProfileVO rebuildProfile(Long userId);
}
