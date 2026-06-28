package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.decision.JobApplyDecisionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 功能: AI 投递决策记录 Mapper。
 */
@Mapper
public interface JobApplyDecisionRecordMapper extends BaseMapper<JobApplyDecisionRecord> {
}
