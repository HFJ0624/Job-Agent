package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.match.JobMatchRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:岗位匹配记录 Mapper
 */
@Mapper
public interface JobMatchRecordMapper extends BaseMapper<JobMatchRecord> {
}
