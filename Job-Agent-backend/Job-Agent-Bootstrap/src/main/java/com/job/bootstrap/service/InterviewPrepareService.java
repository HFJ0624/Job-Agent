package com.job.bootstrap.service;

import com.job.common.vo.interview.InterviewPrepareVO;

/**
 * 作者:hfj
 * 功能:AI 面试准备服务
 */
public interface InterviewPrepareService {

    /**
     * 生成面试准备内容。
     *
     * @param userId 当前用户ID
     * @param applicationId 求职记录ID
     * @param resumeId 简历ID，可为空
     * @return 面试准备结果
     */
    InterviewPrepareVO generatePrepare(Long userId, Long applicationId, Long resumeId);

    /**
     * 查询某条求职记录最近一次面试准备结果。
     *
     * @param userId 当前用户ID
     * @param applicationId 求职记录ID
     * @return 最近一次结果
     */
    InterviewPrepareVO getLatestPrepare(Long userId, Long applicationId);
}
