package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.resume.JobResume;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:简历表 Mapper，负责 resume 表的数据库操作
 * 日期:2026/6/4 10:30
 */
@Mapper
public interface JobResumeMapper extends BaseMapper<JobResume> {
}
