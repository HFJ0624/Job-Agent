package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentTraceLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:Agent Trace 日志 Mapper
 */
@Mapper
public interface AgentTraceLogMapper extends BaseMapper<AgentTraceLog> {
}
