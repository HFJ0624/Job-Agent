package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.user.JobUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:用户表 Mapper，负责 job_user 表的数据库操作
 * 日期:2026/6/2 10:45
 */
@Mapper
public interface JobUserMapper extends BaseMapper<JobUser> {
}
