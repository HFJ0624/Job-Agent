package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.resume.JobResumeScoreRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:简历评分记录 Mapper，负责 resume_score_record 表的数据库操作
 * 日期:2026/6/6
 */
@Mapper
public interface JobResumeScoreRecordMapper extends BaseMapper<JobResumeScoreRecord> {
}