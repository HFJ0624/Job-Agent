package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.job.common.entity.address.JobUserAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 作者:hfj
 * 功能:用户地址表 Mapper，负责 user_address 表的数据库操作
 * 日期:2026/6/4 11:00
 */
@Mapper
public interface JobUserAddressMapper extends BaseMapper<JobUserAddress> {
}
