package com.job.bootstrap.service;

import com.job.common.dto.interview.MockInterviewSessionQueryDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewSessionVO;

import java.util.List;

/**
 * 功能: 后台模拟面试管理服务。
 */
public interface AdminMockInterviewService {

    /**
     * 分页查询模拟面试会话。
     */
    PageResult<MockInterviewSessionVO> pageSessions(MockInterviewSessionQueryDTO query);

    /**
     * 查询会话详情。
     */
    MockInterviewSessionVO getDetail(Long sessionId);

    /**
     * 查询会话下的音频/视频记录。
     */
    List<MockInterviewMediaRecordVO> listMediaRecords(Long sessionId);

    /**
     * 查询某场面试最近一次 AI 复盘详情。
     */
    MockInterviewReviewVO getLatestReview(Long sessionId);

    /**
     * 为某场面试生成或重新生成 AI 复盘详情。
     */
    MockInterviewReviewVO generateReview(Long sessionId);
}
