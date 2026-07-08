package com.job.bootstrap.service;

/**
 * Agent Trace 记录服务接口。
 *
 * <p>核心职责：在 Agent 全生命周期中记录每一次对话、工具调用及内部状态变更，为可观测性提供结构化 Trace 数据。</p>
 *
 * <p>所属业务模块：AI 助手 - 可观测性（Observability）</p>
 *
 * <p>主要调用链：
 * AgentChatService / AgentPlanExecutorService / AgentPlanningService -&gt; AgentTraceService -&gt; AgentTraceServiceImpl -&gt; AgentTraceLogRepository / AgentObservationService</p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>Trace 用于记录 Agent 每一次对话和工具调用过程。</li>
 *   <li>后台可根据 traceId 查看本轮对话调用了哪些工具、输入是什么、输出是什么、是否失败、耗时多久。</li>
 *   <li>这是企业级 Agent 项目非常重要的可观测能力。</li>
 * </ol>
 * </p>
 */
public interface AgentTraceService {

    /**
     * 保存一条 Agent Trace。
     *
     * @param traceId 主链路ID，同一轮对话建议使用同一个 traceId
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param intentCode 意图编码
     * @param toolName 工具名称；普通对话可为空
     * @param input 输入数据
     * @param output 输出数据
     * @param status 状态，建议 SUCCESS / FAILED
     * @param errorMsg 异常信息
     * @param costTime 耗时，单位毫秒
     */
    void saveTrace(
            String traceId,
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    );

    /**
     * 保存工具调用 Trace。
     * 这个方法保留给工具类调用。
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param intentCode 意图编码
     * @param toolName 工具名称
     * @param input 输入数据
     * @param output 输出数据
     * @param status 状态
     * @param errorMsg 异常信息
     * @param costTime 耗时
     */
    void saveToolTrace(
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    );
}
