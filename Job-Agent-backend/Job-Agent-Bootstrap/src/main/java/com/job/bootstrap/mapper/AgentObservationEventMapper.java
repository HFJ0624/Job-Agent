package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentObservationEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件 Mapper
 * 日期: 2026/6/22
 */
@Mapper
public interface AgentObservationEventMapper extends BaseMapper<AgentObservationEvent> {
}
