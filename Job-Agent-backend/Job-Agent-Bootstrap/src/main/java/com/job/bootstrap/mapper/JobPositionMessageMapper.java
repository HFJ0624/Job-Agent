package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.interaction.JobPositionMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:岗位沟通消息 Mapper，负责 job_position_message 表的数据库操作
 * 日期:2026/6/6 16:10
 */
@Mapper
public interface JobPositionMessageMapper extends BaseMapper<JobPositionMessage> {
}
