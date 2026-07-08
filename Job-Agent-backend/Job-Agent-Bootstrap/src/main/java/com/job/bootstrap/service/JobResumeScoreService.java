package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.resume.JobResumeScoreRecord;
import com.job.common.vo.resume.ResumeScoreVO;

/**
 * 简历评分业务服务接口。
 *
 * <p>核心职责：基于用户简历内容与求职方向，调用 AI 模型进行多维度评分，并管理评分记录。</p>
 *
 * <p>所属业务模块：求职辅助 - 简历评估</p>
 *
 * <p>主要调用链：
 * ResumeController / JobApplicationController -&gt; JobResumeScoreService -&gt; JobResumeScoreServiceImpl -&gt; AiModelGatewayService / JobResumeService / JobResumeScoreRecordRepository</p>
 */
public interface JobResumeScoreService extends IService<JobResumeScoreRecord> {

    /**
     * 对当前用户的指定简历进行评分。
     *
     * @param userId 当前登录用户ID
     * @param resumeId 简历ID
     * @param targetPosition 求职方向，可为空
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
