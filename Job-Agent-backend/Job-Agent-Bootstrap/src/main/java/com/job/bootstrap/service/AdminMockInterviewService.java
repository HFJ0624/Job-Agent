package com.job.bootstrap.service;

import com.job.common.dto.interview.MockInterviewSessionQueryDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewSessionVO;

import java.util.List;

/**
 * 后台模拟面试管理服务。
 *
 * <p>核心职责：为管理员提供模拟面试会话的全生命周期管理能力，包括会话查询、媒体记录查看、AI 复盘生成与查询。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试后台管理</p>
 *
 * <p>主要调用链：Admin Controller → AdminMockInterviewService → 模拟面试领域 Service / Mapper</p>
 */
public interface AdminMockInterviewService {

    /**
     * 分页查询模拟面试会话列表。
     *
     * @param query 查询条件（包含用户 ID、时间范围、面试类型等过滤条件）
     * @return 模拟面试会话分页结果
     */
    PageResult<MockInterviewSessionVO> pageSessions(MockInterviewSessionQueryDTO query);

    /**
     * 查询指定模拟面试会话详情。
     *
     * @param sessionId 模拟面试会话 ID
     * @return 会话完整详情，包含题目列表、答题记录、评分信息
     */
    MockInterviewSessionVO getDetail(Long sessionId);

    /**
     * 查询指定会话下的音频/视频录制记录。
     *
     * @param sessionId 模拟面试会话 ID
     * @return 媒体记录列表，包含录制文件 URL、时长、类型等信息
     */
    List<MockInterviewMediaRecordVO> listMediaRecords(Long sessionId);

    /**
     * 查询某场模拟面试最近一次 AI 复盘详情。
     *
     * @param sessionId 模拟面试会话 ID
     * @return AI 复盘报告，包含综合评分、能力维度分析、改进建议
     */
    MockInterviewReviewVO getLatestReview(Long sessionId);

    /**
     * 为某场模拟面试生成或重新生成 AI 复盘报告。
     *
     * @param sessionId 模拟面试会话 ID
     * @return 新生成的 AI 复盘报告
     */
    MockInterviewReviewVO generateReview(Long sessionId);
}
