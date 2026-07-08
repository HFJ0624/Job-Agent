package com.job.bootstrap.service;

import com.job.common.vo.interview.InterviewPrepareVO;

/**
 * AI 面试准备服务接口。
 *
 * <p>核心职责：基于求职记录、简历和岗位信息，调用 AI 模型生成个性化面试准备材料，包括常见问题、技术要点及答题建议。</p>
 *
 * <p>所属业务模块：求职辅助 - 面试准备</p>
 *
 * <p>主要调用链：
 * InterviewController -&gt; InterviewPrepareService -&gt; InterviewPrepareServiceImpl -&gt; AiModelGatewayService / JobApplicationService / JobResumeService</p>
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
