package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentTraceRetentionPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者: hfj
 * 功能: Agent Trace 保留策略 Mapper
 * 日期: 2026/6/22
 */
@Mapper
public interface AgentTraceRetentionPolicyMapper extends BaseMapper<AgentTraceRetentionPolicy> {
}
