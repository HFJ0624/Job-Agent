package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewStudyPlanItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟面试学习计划每日任务 Mapper。
 */
@Mapper
public interface MockInterviewStudyPlanItemMapper extends BaseMapper<MockInterviewStudyPlanItem> {
}
