package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewMediaRecordMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.service.AdminMockInterviewService;
import com.job.common.dto.interview.MockInterviewSessionQueryDTO;
import com.job.common.entity.base.PageResult;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewMediaRecord;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewMediaRecordVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 功能: 后台模拟面试管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminMockInterviewServiceImpl implements AdminMockInterviewService {

    private static final int NOT_DELETED = 0;

    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final MockInterviewMediaRecordMapper mediaRecordMapper;

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

    @Override
    public MockInterviewSessionVO getDetail(Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || (session.getIsDeleted() != null && session.getIsDeleted() == 1)) {
            throw new BizException("模拟面试会话不存在");
        }

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
}
