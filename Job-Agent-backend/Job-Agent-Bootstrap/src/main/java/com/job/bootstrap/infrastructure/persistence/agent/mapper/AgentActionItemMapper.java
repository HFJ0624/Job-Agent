package com.job.bootstrap.infrastructure.persistence.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentActionItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 行动确认项 Mapper。
 */
@Mapper
public interface AgentActionItemMapper extends BaseMapper<AgentActionItem> {
}
