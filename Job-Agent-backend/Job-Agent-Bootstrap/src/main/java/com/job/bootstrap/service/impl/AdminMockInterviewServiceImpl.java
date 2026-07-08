package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewMediaRecordMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.service.AdminMockInterviewService;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.dto.interview.MockInterviewSessionQueryDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewMediaRecord;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台模拟面试管理服务实现。
 *
 * <p>核心职责：为管理后台提供模拟面试会话的分页查询、详情查看、媒体记录列表、复盘查询与生成能力。
 *
 * <p>所属业务模块：面试训练中心 - 模拟面试管理（Admin Mock Interview）。
 *
 * <p>主要调用链：
 * <ul>
 *   <li>列表：{@code pageSessions} → 分页返回会话摘要。</li>
 *   <li>详情：{@code getDetail} → 聚合会话、题目、回答、音频记录。</li>
 *   <li>复盘：{@code getLatestReview / generateReview} → 复用用户端复盘服务。</li>
 * </ul>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link MockInterviewReviewService}：前后台共用同一复盘服务，保证展示字段和计算口径一致。</li>
 *   <li>{@link MockInterviewSessionMapper} / {@link MockInterviewQuestionMapper} / {@link MockInterviewAnswerMapper}：读取原始面试数据。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>后台入口只有 sessionId，先读取会话获取 userId，再透传给用户端服务，保证数据权限校验不绕过。</li>
 *   <li>列表页只查会话摘要，不带题目和音频，避免大数据量拖慢管理台。</li>
 *   <li>详情和复盘直接复用用户端实现，避免前后台逻辑分叉。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminMockInterviewServiceImpl implements AdminMockInterviewService {

    private static final int NOT_DELETED = 0;

    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final MockInterviewMediaRecordMapper mediaRecordMapper;
    private final MockInterviewReviewService mockInterviewReviewService;

    /**
     * 分页查询模拟面试会话列表。
     *
     * <p>支持按用户、岗位、简历、状态及关键词（岗位/公司名）过滤，仅返回会话摘要。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<MockInterviewSessionVO> pageSessions(MockInterviewSessionQueryDTO query) {
        Long pageNum = query.getPageNum() == null ? 1L : query.getPageNum();
        Long pageSize = query.getPageSize() == null ? 10L : query.getPageSize();

        // 1. 后台分页只查会话摘要，避免列表页一次性带出大量题目和音频记录。
        LambdaQueryWrapper<MockInterviewSession> wrapper = new LambdaQueryWrapper<MockInterviewSession>()
                .eq(MockInterviewSession::getIsDeleted, NOT_DELETED)
                .eq(query.getUserId() != null, MockInterviewSession::getUserId, query.getUserId())
                .eq(query.getJobId() != null, MockInterviewSession::getJobId, query.getJobId())
                .eq(query.getResumeId() != null, MockInterviewSession::getResumeId, query.getResumeId())
                .eq(StringUtils.hasText(query.getStatus()), MockInterviewSession::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), item -> item
                        .like(MockInterviewSession::getJobTitle, query.getKeyword())
                        .or()
                        .like(MockInterviewSession::getCompanyName, query.getKeyword()))
                .orderByDesc(MockInterviewSession::getCreateTime);

        IPage<MockInterviewSession> page = sessionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<MockInterviewSessionVO> records = page.getRecords().stream()
                .map(MockInterviewSessionVO::from)
                .toList();
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 获取模拟面试会话完整详情。
     *
     * <p>聚合会话、题目、回答及音频媒体记录，供管理台详情页展示。
     *
     * @param sessionId 会话 ID
     * @return 会话详情 VO
     */
    @Override
    public MockInterviewSessionVO getDetail(Long sessionId) {
        MockInterviewSession session = getExistingSession(sessionId);

        List<MockInterviewQuestion> questions = questionMapper.selectList(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getSessionId, sessionId)
                .eq(MockInterviewQuestion::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewQuestion::getSortNo));
        List<MockInterviewAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<MockInterviewAnswer>()
                .eq(MockInterviewAnswer::getSessionId, sessionId)
                .eq(MockInterviewAnswer::getIsDeleted, NOT_DELETED)
                .orderByAsc(MockInterviewAnswer::getCreateTime));

        MockInterviewSessionVO vo = MockInterviewSessionVO.from(session);
        vo.setQuestions(questions.stream().map(MockInterviewQuestionVO::from).toList());
        vo.setAnswers(answers.stream().map(MockInterviewAnswerVO::from).toList());
        vo.setMediaRecords(listMediaRecords(sessionId));
        return vo;
    }

    /**
     * 查询指定会话的所有音频媒体记录。
     *
     * @param sessionId 会话 ID
     * @return 媒体记录列表
     */
    @Override
    public List<MockInterviewMediaRecordVO> listMediaRecords(Long sessionId) {
        return mediaRecordMapper.selectList(new LambdaQueryWrapper<MockInterviewMediaRecord>()
                        .eq(MockInterviewMediaRecord::getSessionId, sessionId)
                        .eq(MockInterviewMediaRecord::getIsDeleted, NOT_DELETED)
                        .orderByAsc(MockInterviewMediaRecord::getCreateTime))
                .stream()
                .map(MockInterviewMediaRecordVO::from)
                .toList();
    }

    /**
     * 查询指定会话最近一次复盘报告。
     *
     * <p>后台入口只有 sessionId，先读取会话获取 userId，再透传用户端复盘服务，保证数据权限校验不绕过。
     *
     * @param sessionId 会话 ID
     * @return 复盘报告 VO
     */
    @Override
    public MockInterviewReviewVO getLatestReview(Long sessionId) {
        MockInterviewSession session = getExistingSession(sessionId);
        return mockInterviewReviewService.getLatestReview(session.getUserId(), sessionId);
    }

    /**
     * 为指定会话触发 AI 复盘报告生成。
     *
     * <p>确认会话有效后，复用用户端复盘服务完成真实模型调用、JSON 解析及复盘记录入库。
     *
     * @param sessionId 会话 ID
     * @return 生成的复盘报告 VO
     */
    @Override
    public MockInterviewReviewVO generateReview(Long sessionId) {
        MockInterviewSession session = getExistingSession(sessionId);
        return mockInterviewReviewService.generateReview(session.getUserId(), sessionId);
    }

    /**
     * 查询有效面试会话。
     *
     * 步骤:
     * 1. 根据主键读取 mock_interview_session。
     * 2. 统一过滤不存在或已逻辑删除的会话。
     * 3. 供详情、复盘查询、复盘生成共用，避免多个入口各写一遍校验。
     */
    private MockInterviewSession getExistingSession(Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || (session.getIsDeleted() != null && session.getIsDeleted() == 1)) {
            throw new BizException("模拟面试会话不存在");
        }
        return session;
    }
}
