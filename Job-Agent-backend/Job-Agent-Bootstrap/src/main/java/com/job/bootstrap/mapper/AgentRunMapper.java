package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * 功能: Agent run Mapper（第一阶段 ReAct + Workflow）。
 */
@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {
}
