package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.resume.JobResumeScoreRecord;
import com.job.common.vo.resume.ResumeScoreVO;

/**
 * 作者:hfj
 * 功能:简历评分业务服务接口
 * 日期:2026/6/6
 */
public interface JobResumeScoreService extends IService<JobResumeScoreRecord> {

    /**
     * 对当前用户的指定简历进行评分。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @param targetPosition 目标岗位，可为空
     * @return 返回评分结果VO
     */
    ResumeScoreVO scoreResume(Long userId, Long resumeId, String targetPosition);

    /**
     * 查询当前用户某份简历最近一次评分结果。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @return 返回最近一次评分结果，没有评分记录时返回 null
     */
    ResumeScoreVO getLatestScore(Long userId, Long resumeId);
}
