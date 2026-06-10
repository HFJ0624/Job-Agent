package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.application.JobApplicationQueryDTO;
import com.job.common.dto.application.JobApplicationSaveDTO;
import com.job.common.dto.application.JobApplicationStatusUpdateDTO;
import com.job.common.vo.application.JobApplicationStatsVO;
import com.job.common.vo.application.JobApplicationVO;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:求职投递记录服务
 */
public interface JobApplicationService {

    /**
     * 新增或更新求职记录。
     *
     * @param userId 当前用户ID
     * @param dto 保存参数
     * @return 求职记录
     */
    JobApplicationVO saveApplication(Long userId, JobApplicationSaveDTO dto);

    /**
     * 分页查询求职记录。
     *
     * @param userId 当前用户ID
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<JobApplicationVO> pageApplications(Long userId, JobApplicationQueryDTO query);

    /**
     * 修改求职状态。
     *
     * @param userId 当前用户ID
     * @param id 记录ID
     * @param dto 状态更新参数
     * @return 更新后的记录
     */
    JobApplicationVO updateStatus(Long userId, Long id, JobApplicationStatusUpdateDTO dto);

    /**
     * 删除求职记录。
     *
     * @param userId 当前用户ID
     * @param id 记录ID
     */
    void deleteApplication(Long userId, Long id);

    /**
     * 查询求职进度统计。
     *
     * @param userId 当前用户ID
     * @return 统计结果
     */
    JobApplicationStatsVO getStats(Long userId);

    /***
     *
     * @param userId 用户id
     * @param applicationId 面试求职id
     * @param interviewTime 面试时间
     * @param nextFollowTime 下次跟进时间
     */
    void syncInterviewProgress(Long userId, Long applicationId, Date interviewTime, Date nextFollowTime);
}
