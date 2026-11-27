package com.job.bootstrap.infrastructure.persistence.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewStudyPlanRetest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习计划复测记录 Mapper。
 */
@Mapper
public interface MockInterviewStudyPlanRetestMapper extends BaseMapper<MockInterviewStudyPlanRetest> {
}
