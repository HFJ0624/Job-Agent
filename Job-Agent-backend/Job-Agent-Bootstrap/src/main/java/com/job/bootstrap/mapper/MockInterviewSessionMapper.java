package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interview.MockInterviewSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:模拟面试会话 Mapper
 */
@Mapper
public interface MockInterviewSessionMapper extends BaseMapper<MockInterviewSession> {
}
