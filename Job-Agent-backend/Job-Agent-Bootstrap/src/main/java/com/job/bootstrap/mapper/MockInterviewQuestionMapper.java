package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:模拟面试题目 Mapper
 */
@Mapper
public interface MockInterviewQuestionMapper extends BaseMapper<MockInterviewQuestion> {
}
