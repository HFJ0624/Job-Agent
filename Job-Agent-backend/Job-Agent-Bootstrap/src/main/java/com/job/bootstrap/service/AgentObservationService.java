package com.job.bootstrap.service;

import com.job.bootstrap.observability.AgentObservationRecord;

/**
 * Agent 统一观测事件写入服务接口。
 *
 * <p>核心职责：在 Agent 执行链路中埋点，异步记录观测事件，为后续故障排查、性能分析和告警提供原始数据。</p>
 *
 * <p>所属业务模块：AI 助手 - 可观测性（Observability）</p>
 *
 * <p>主要调用链：
 * AgentTraceService / AgentPlanExecutorService / AgentChatService -&gt; AgentObservationService -&gt; AgentObservationServiceImpl -&gt; AgentObservationEventRepository / MQ</p>
 */
public interface AgentObservationService {

    /**
     * 记录一条观测事件。
     *
     * @param record 观测事件参数
     */
    void recordEvent(AgentObservationRecord record);
}
