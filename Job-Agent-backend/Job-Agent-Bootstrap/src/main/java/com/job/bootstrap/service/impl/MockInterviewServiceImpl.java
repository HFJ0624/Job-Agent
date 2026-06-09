package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.MockInterviewService;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.InterviewPrepareVO;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 作者:hfj
 * 功能:模拟面试服务实现
 *
 * 说明:
 * 1. 第一版使用规则评分，保证稳定可用。
 * 2. 题目来源优先使用 AI 面试准备记录。
 * 3. 回答评分从完整度、结构化表达、量化结果、技术关键词等维度计算。
 */
@Service
@RequiredArgsConstructor
public class MockInterviewServiceImpl implements MockInterviewService {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_FINISHED = "FINISHED";

    private static final String TYPE_TECHNICAL = "TECHNICAL";
    private static final String TYPE_PROJECT = "PROJECT";
    private static final String TYPE_HR = "HR";

    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final JobApplicationRecordMapper applicationMapper;
    private final InterviewPrepareService interviewPrepareService;

    /**
     * 开始模拟面试。
     */
    @Override
    public MockInterviewSessionVO startSession(Long userId, MockInterviewStartDTO dto) {
        JobApplicationRecord application = applicationMapper.selectById(dto.getApplicationId());

        if (application == null || !userId.equals(application.getUserId())) {
            throw new BizException("求职记录不存在或无权限访问");
        }

        /*
         * 1. 优先查询最近一次面试准备。
         * 2. 如果没有，则自动生成一份面试准备。
         */
        InterviewPrepareVO prepare = interviewPrepareService.getLatestPrepare(userId, dto.getApplicationId());

        if (prepare == null) {
            prepare = interviewPrepareService.generatePrepare(
                    userId,
                    dto.getApplicationId(),
                    dto.getResumeId()
            );
        }

        /*
         * 控制题目数量，避免一次模拟面试太长。
         */
        int questionCount = dto.getQuestionCount() == null ? 6 : dto.getQuestionCount();
        questionCount = Math.max(3, Math.min(questionCount, 12));

        List<QuestionSeed> seeds = buildQuestionSeeds(prepare, questionCount);

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setApplicationId(application.getId());
        session.setInterviewPrepareId(prepare.getId());
        session.setJobId(application.getJobId());
        session.setResumeId(dto.getResumeId() != null ? dto.getResumeId() : application.getResumeId());
        session.setJobTitle(application.getJobTitle());
        session.setCompanyName(application.getCompanyName());
        session.setStatus(STATUS_IN_PROGRESS);
        session.setCurrentIndex(0);
        session.setTotalQuestionCount(seeds.size());
        session.setIsDeleted(0);

        sessionMapper.insert(session);

        /*
         * 保存题目。
         */
        int sortNo = 1;
        for (QuestionSeed seed : seeds) {
            MockInterviewQuestion question = new MockInterviewQuestion();
            question.setSessionId(session.getId());
            question.setUserId(userId);
            question.setQuestionType(seed.type());
            question.setQuestionContent(seed.content());
            question.setSortNo(sortNo++);
            question.setAnswered(0);
            question.setIsDeleted(0);
            questionMapper.insert(question);
        }

        return getSessionDetail(userId, session.getId());
    }

    /**
     * 查询面试详情。
     */
    @Override
    public MockInterviewSessionVO getSessionDetail(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);

        List<MockInterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getSessionId, sessionId)
                        .eq(MockInterviewQuestion::getUserId, userId)
                        .orderByAsc(MockInterviewQuestion::getSortNo)
        );

        List<MockInterviewAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<MockInterviewAnswer>()
                        .eq(MockInterviewAnswer::getSessionId, sessionId)
                        .eq(MockInterviewAnswer::getUserId, userId)
                        .orderByAsc(MockInterviewAnswer::getCreateTime)
        );

        MockInterviewSessionVO vo = MockInterviewSessionVO.from(session);
        vo.setQuestions(questions.stream().map(MockInterviewQuestionVO::from).toList());
        vo.setAnswers(answers.stream().map(MockInterviewAnswerVO::from).toList());
        return vo;
    }

    /**
     * 查询当前题目。
     */
    @Override
    public MockInterviewQuestionVO getCurrentQuestion(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);

        if (STATUS_FINISHED.equals(session.getStatus())) {
            return null;
        }

        MockInterviewQuestion question = questionMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getSessionId, sessionId)
                        .eq(MockInterviewQuestion::getUserId, userId)
                        .eq(MockInterviewQuestion::getAnswered, 0)
                        .orderByAsc(MockInterviewQuestion::getSortNo)
                        .last("limit 1")
        );

        return MockInterviewQuestionVO.from(question);
    }

    /**
     * 提交回答。
     */
    @Override
    public MockInterviewAnswerVO submitAnswer(Long userId, Long sessionId, MockInterviewAnswerDTO dto) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);

        if (STATUS_FINISHED.equals(session.getStatus())) {
            throw new BizException("本轮模拟面试已经结束");
        }

        MockInterviewQuestion question = questionMapper.selectById(dto.getQuestionId());

        if (question == null
                || !sessionId.equals(question.getSessionId())
                || !userId.equals(question.getUserId())) {
            throw new BizException("题目不存在或无权限回答");
        }

        if (question.getAnswered() != null && question.getAnswered() == 1) {
            throw new BizException("该题已经回答过");
        }

        /*
         * 规则评分。
         */
        AnswerEvaluation evaluation = evaluateAnswer(question, dto.getAnswerContent());

        MockInterviewAnswer answer = new MockInterviewAnswer();
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setUserId(userId);
        answer.setAnswerContent(dto.getAnswerContent());
        answer.setScore(evaluation.score());
        answer.setLevel(evaluation.level());
        answer.setStrengths(String.join("\n", evaluation.strengths()));
        answer.setProblems(String.join("\n", evaluation.problems()));
        answer.setSuggestions(String.join("\n", evaluation.suggestions()));
        answer.setIsDeleted(0);

        answerMapper.insert(answer);

        /*
         * 标记题目已回答。
         */
        question.setAnswered(1);
        questionMapper.updateById(question);

        /*
         * 更新会话进度。
         */
        session.setCurrentIndex(session.getCurrentIndex() + 1);

        if (session.getCurrentIndex() >= session.getTotalQuestionCount()) {
            finishAndScoreSession(session);
        } else {
            sessionMapper.updateById(session);
        }

        return MockInterviewAnswerVO.from(answer);
    }

    /**
     * 手动结束面试。
     */
    @Override
    public MockInterviewSessionVO finishSession(Long userId, Long sessionId) {
        MockInterviewSession session = getUserSessionRequired(userId, sessionId);

        finishAndScoreSession(session);

        return getSessionDetail(userId, sessionId);
    }

    /**
     * 生成题目种子。
     */
    private List<QuestionSeed> buildQuestionSeeds(InterviewPrepareVO prepare, int questionCount) {
        List<QuestionSeed> seeds = new ArrayList<>();

        /*
         * 技术题优先取 3 道。
         */
        for (String item : prepare.getTechnicalQuestions()) {
            seeds.add(new QuestionSeed(TYPE_TECHNICAL, item));
        }

        /*
         * 项目题优先取 2 道。
         */
        for (String item : prepare.getProjectQuestions()) {
            seeds.add(new QuestionSeed(TYPE_PROJECT, item));
        }

        /*
         * HR题补充。
         */
        for (String item : prepare.getHrQuestions()) {
            seeds.add(new QuestionSeed(TYPE_HR, item));
        }

        /*
         * 去重并限制数量。
         */
        return seeds.stream()
                .filter(seed -> StringUtils.hasText(seed.content()))
                .distinct()
                .limit(questionCount)
                .toList();
    }

    /**
     * 回答评分。
     */
    private AnswerEvaluation evaluateAnswer(MockInterviewQuestion question, String answerContent) {
        String answer = answerContent == null ? "" : answerContent.trim();

        double score = 40;
        List<String> strengths = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        /*
         * 1. 回答完整度。
         */
        if (answer.length() >= 120) {
            score += 20;
            strengths.add("回答内容较完整，具备一定展开。");
        } else if (answer.length() >= 60) {
            score += 10;
            strengths.add("回答具备基本内容，但展开还不够充分。");
        } else {
            problems.add("回答偏短，信息量不足。");
            suggestions.add("建议至少按照“背景、做法、结果”展开回答。");
        }

        /*
         * 2. 结构化表达。
         */
        if (containsAny(answer, List.of("首先", "其次", "最后", "第一", "第二", "背景", "方案", "结果"))) {
            score += 15;
            strengths.add("回答有一定结构，便于面试官理解。");
        } else {
            problems.add("回答结构不够清晰。");
            suggestions.add("建议使用“背景-方案-职责-结果”或“问题-分析-解决-效果”的结构。");
        }

        /*
         * 3. 项目结果或量化表达。
         */
        if (containsAny(answer, List.of("%", "提升", "降低", "优化", "QPS", "响应时间", "并发", "ms", "数据量"))) {
            score += 15;
            strengths.add("回答中包含结果或量化描述，说服力较强。");
        } else {
            suggestions.add("可以补充量化结果，例如性能提升比例、响应时间、数据量或业务效果。");
        }

        /*
         * 4. 技术关键词。
         */
        if (containsAny(answer, List.of("Java", "Spring", "MySQL", "Redis", "接口", "缓存", "事务", "索引", "消息队列"))) {
            score += 10;
            strengths.add("回答中体现了一定技术关键词。");
        } else if (TYPE_TECHNICAL.equals(question.getQuestionType())) {
            problems.add("技术题回答中缺少明确技术关键词。");
            suggestions.add("回答技术题时建议明确说出使用的框架、组件、原理或排查方法。");
        }

        BigDecimal finalScore = BigDecimal.valueOf(Math.min(score, 100))
                .setScale(2, RoundingMode.HALF_UP);

        String level = resolveLevel(finalScore);

        if (strengths.isEmpty()) {
            strengths.add("回答已经覆盖了部分问题方向。");
        }

        if (problems.isEmpty()) {
            problems.add("暂无明显问题，可以继续提升表达细节。");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("继续保持结构化表达，并结合项目细节进行说明。");
        }

        return new AnswerEvaluation(finalScore, level, strengths, problems, suggestions);
    }

    /**
     * 结束会话并计算总分。
     */
    private void finishAndScoreSession(MockInterviewSession session) {
        List<MockInterviewAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<MockInterviewAnswer>()
                        .eq(MockInterviewAnswer::getSessionId, session.getId())
                        .eq(MockInterviewAnswer::getUserId, session.getUserId())
        );

        BigDecimal totalScore = BigDecimal.ZERO;

        if (!answers.isEmpty()) {
            BigDecimal sum = answers.stream()
                    .map(MockInterviewAnswer::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalScore = sum.divide(
                    BigDecimal.valueOf(answers.size()),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        session.setStatus(STATUS_FINISHED);
        session.setTotalScore(totalScore);
        session.setSummary(buildSessionSummary(totalScore));
        sessionMapper.updateById(session);
    }

    /**
     * 生成总评。
     */
    private String buildSessionSummary(BigDecimal totalScore) {
        double value = totalScore.doubleValue();

        if (value >= 85) {
            return "本轮模拟面试表现较好，回答较完整，建议继续加强项目细节和量化表达。";
        }

        if (value >= 70) {
            return "本轮模拟面试表现中等偏上，具备基本表达能力，但部分回答还可以更结构化。";
        }

        if (value >= 60) {
            return "本轮模拟面试表现一般，建议重点练习项目介绍、技术原理和回答结构。";
        }

        return "本轮模拟面试表现偏弱，建议先整理简历项目话术，再进行下一轮模拟。";
    }

    /**
     * 查询当前用户的面试会话。
     */
    private MockInterviewSession getUserSessionRequired(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);

        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException("模拟面试会话不存在或无权限访问");
        }

        return session;
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lowerText = text == null ? "" : text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String resolveLevel(BigDecimal score) {
        double value = score.doubleValue();

        if (value >= 85) {
            return "优秀";
        }
        if (value >= 70) {
            return "良好";
        }
        if (value >= 60) {
            return "一般";
        }
        return "待提升";
    }

    /**
     * 题目种子。
     */
    private record QuestionSeed(
            String type,
            String content
    ) {
    }

    /**
     * 回答评分中间结果。
     */
    private record AnswerEvaluation(
            BigDecimal score,
            String level,
            List<String> strengths,
            List<String> problems,
            List<String> suggestions
    ) {
    }
}
