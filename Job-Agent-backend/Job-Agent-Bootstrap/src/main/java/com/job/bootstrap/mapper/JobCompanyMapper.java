package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.company.JobCompany;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:公司表 Mapper，负责 company 表的增删改查数据库操作
 * 日期:2026/6/6 10:30
 */
@Mapper
public interface JobCompanyMapper extends BaseMapper<JobCompany> {
}
