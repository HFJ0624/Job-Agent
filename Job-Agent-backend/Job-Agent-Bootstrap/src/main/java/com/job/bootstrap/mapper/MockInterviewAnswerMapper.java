package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewAnswer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:模拟面试回答 Mapper
 */
@Mapper
public interface MockInterviewAnswerMapper extends BaseMapper<MockInterviewAnswer> {
}
