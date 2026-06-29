package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewStudyPlan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟面试学习计划主表 Mapper。
 */
@Mapper
public interface MockInterviewStudyPlanMapper extends BaseMapper<MockInterviewStudyPlan> {
}
