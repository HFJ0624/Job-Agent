package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentInboxActionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Inbox 待办处理记录 Mapper。
 */
@Mapper
public interface AgentInboxActionRecordMapper extends BaseMapper<AgentInboxActionRecord> {
}
