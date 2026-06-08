package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.application.JobApplicationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:求职投递记录 Mapper
 */
@Mapper
public interface JobApplicationRecordMapper extends BaseMapper<JobApplicationRecord> {
}
