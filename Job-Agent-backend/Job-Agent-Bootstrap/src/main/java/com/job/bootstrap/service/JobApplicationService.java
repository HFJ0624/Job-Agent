package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.application.JobApplicationQueryDTO;
import com.job.common.dto.application.JobApplicationSaveDTO;
import com.job.common.dto.application.JobApplicationStatusUpdateDTO;
import com.job.common.vo.application.JobApplicationStatsVO;
import com.job.common.vo.application.JobApplicationVO;

import java.util.Date;

/**
 * 求职投递记录服务接口。
 *
 * <p>核心职责：管理用户从投递到录用全生命周期的求职记录，包括增删改查、状态流转、进度统计及面试日程同步。</p>
 *
 * <p>所属业务模块：求职管理 - 投递跟踪</p>
 *
 * <p>主要调用链：
 * JobApplicationController -&gt; JobApplicationService -&gt; JobApplicationServiceImpl -&gt; JobApplicationRepository / JobReminderService / JobCommunicationRecordService</p>
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

    /**
     * 同步面试进度信息到求职记录。
     *
     * <p>场景：用户确认面试邀约后，将面试时间和下次跟进时间回写到对应求职记录，并触发提醒创建。</p>
     *
     * @param userId 当前用户 ID
     * @param applicationId 求职记录 ID
     * @param interviewTime 面试时间
     * @param nextFollowTime 下次跟进时间
     */
    void syncInterviewProgress(Long userId, Long applicationId, Date interviewTime, Date nextFollowTime);
}
