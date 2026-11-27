package com.job.bootstrap.infrastructure.persistence.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.InterviewQuestionBank;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 模拟面试题库 Mapper。
 */
@Mapper
public interface InterviewQuestionBankMapper extends BaseMapper<InterviewQuestionBank> {
}
