package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.agent.AgentDailyReportSubscription;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 主动日报订阅 Mapper。
 */
@Mapper
public interface AgentDailyReportSubscriptionMapper extends BaseMapper<AgentDailyReportSubscription> {
}
