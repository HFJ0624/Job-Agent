package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.greeting.JobGreetingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:HR 打招呼语生成记录 Mapper
 */
@Mapper
public interface JobGreetingRecordMapper extends BaseMapper<JobGreetingRecord> {
}
