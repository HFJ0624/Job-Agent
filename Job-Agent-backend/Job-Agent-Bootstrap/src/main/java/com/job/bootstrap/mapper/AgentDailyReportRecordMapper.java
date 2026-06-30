package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentDailyReportRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 每日求职日报 Mapper。
 */
@Mapper
public interface AgentDailyReportRecordMapper extends BaseMapper<AgentDailyReportRecord> {
}
