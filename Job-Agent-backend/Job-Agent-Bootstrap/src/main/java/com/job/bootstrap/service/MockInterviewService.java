package com.job.bootstrap.service;

import com.job.common.dto.interview.AiInterviewStartDTO;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模拟面试服务。
 *
 * <p>核心职责：为用户提供完整的模拟面试会话管理能力，覆盖面试启动（文本/语音）、题目获取、答案提交（文本/语音）、实时评分、会话结束等全生命周期流程。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试核心引擎</p>
 *
 * <p>主要调用链：Front Controller → MockInterviewService → 题目选择 Service / LLM 评分引擎 / 语音转文字 Service / 面试记录 Mapper</p>
 */
public interface MockInterviewService {

    /**
     * 开始一轮新的模拟面试会话。
     *
     * @param userId 当前用户 ID
     * @param dto    面试启动参数（包含岗位、简历、面试类型、题目数量等）
     * @return 新创建的模拟面试会话详情，包含会话 ID、首轮题目等
     */
    MockInterviewSessionVO startSession(Long userId, MockInterviewStartDTO dto);

    /**
     * 按简历和岗位直接开始 AI 语音面试。
     *
     * @param userId 当前用户 ID
     * @param dto    AI 语音面试启动参数（包含岗位、简历、语音设置等）
     * @return 新创建的 AI 语音面试会话详情
     */
    MockInterviewSessionVO startAiInterview(Long userId, AiInterviewStartDTO dto);

    /**
     * 查询指定模拟面试会话详情。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 会话完整详情，包含当前进度、历史答题记录、评分汇总等
     */
    MockInterviewSessionVO getSessionDetail(Long userId, Long sessionId);

    /**
     * 查询当前会话中用户应回答的题目。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 当前轮次的面试题目，包含题干、考察点、答题限时等
     */
    MockInterviewQuestionVO getCurrentQuestion(Long userId, Long sessionId);

    /**
     * 提交文本答案并触发 AI 评分。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @param dto       答案提交参数（包含题目 ID、答案内容、答题用时等）
     * @return 评分结果，包含得分、点评、改进建议、标准答案对比
     */
    MockInterviewAnswerVO submitAnswer(Long userId, Long sessionId, MockInterviewAnswerDTO dto);

    /**
     * 上传某道题的语音回答，经语音识别转文字后复用文本评分链路进行评分。
     *
     * @param userId          当前用户 ID
     * @param sessionId       模拟面试会话 ID
     * @param questionId      题目 ID
     * @param audio           语音文件（Multipart 格式）
     * @param durationSeconds 语音时长（秒）
     * @return 语音识别后的文本评分结果
     */
    MockInterviewAnswerVO submitAudioAnswer(Long userId, Long sessionId, Long questionId, MultipartFile audio, Integer durationSeconds);

    /**
     * 手动结束指定模拟面试会话。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 结束后的会话详情，包含最终评分、答题统计、复盘入口等
     */
    MockInterviewSessionVO finishSession(Long userId, Long sessionId);
}
