package com.job.bootstrap.service;

import com.job.bootstrap.observability.AgentObservationRecord;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件写入服务
 * 日期: 2026/6/22
 */
public interface AgentObservationService {

    /**
     * 记录一条观测事件。
     *
     * @param record 观测事件参数
     */
    void recordEvent(AgentObservationRecord record);
}
