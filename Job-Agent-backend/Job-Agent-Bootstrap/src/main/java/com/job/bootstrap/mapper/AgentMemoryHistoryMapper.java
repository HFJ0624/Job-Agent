package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentMemoryHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆版本历史 Mapper
 * 日期: 2026/6/25
 */
@Mapper
public interface AgentMemoryHistoryMapper extends BaseMapper<AgentMemoryHistory> {
}
