package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.communication.JobCommunicationMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者: hfj
 * 功能: 求职沟通消息流水 Mapper
 */
@Mapper
public interface JobCommunicationMessageMapper extends BaseMapper<JobCommunicationMessage> {
}
