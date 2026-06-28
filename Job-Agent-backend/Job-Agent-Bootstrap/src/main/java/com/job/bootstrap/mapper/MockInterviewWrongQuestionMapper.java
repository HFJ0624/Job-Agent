package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewWrongQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟面试错题本 Mapper。
 */
@Mapper
public interface MockInterviewWrongQuestionMapper extends BaseMapper<MockInterviewWrongQuestion> {
}
