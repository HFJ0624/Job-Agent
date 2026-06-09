package com.job.bootstrap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.common.dto.communication.JobCommunicationQueryDTO;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.common.vo.communication.JobCommunicationRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 作者: hfj
 * 功能: 求职沟通记录 Mapper
 */
@Mapper
public interface JobCommunicationRecordMapper extends BaseMapper<JobCommunicationRecord> {

    /**
     * 分页查询沟通记录展示列表。
     * 关联表:
     * 1. job_communication_record 沟通记录表
     * 2. resume 简历表
     * 3. job_position 岗位表
     * 4. company 公司表
     *
     * @param page MyBatis-Plus 分页对象
     * @param userId 当前登录用户ID
     * @param queryDTO 查询条件
     * @return 沟通记录展示分页
     */
    Page<JobCommunicationRecordVO> selectCommunicationPage(
            Page<JobCommunicationRecordVO> page,
            @Param("userId") Long userId,
            @Param("query") JobCommunicationQueryDTO queryDTO
    );

    /**
     * 查询沟通记录详情。
     *
     * @param userId 当前登录用户ID
     * @param id 沟通记录ID
     * @return 沟通记录详情
     */
    JobCommunicationRecordVO selectCommunicationDetail(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
