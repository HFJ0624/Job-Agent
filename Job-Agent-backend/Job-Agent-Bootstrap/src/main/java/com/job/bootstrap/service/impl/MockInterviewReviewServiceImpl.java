package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewReviewRecordMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewReviewRecord;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 作者:hfj
 * 功能:模拟面试复盘报告服务实现
 * 设计思路:
 * 1. 从 mock_interview_session 读取本轮会话。
 * 2. 从 mock_interview_answer 读取所有回答评分。
 * 3. 从 mock_interview_question 读取题目内容。
 * 4. 根据分数、问题、建议生成复盘报告。
 */
@Service
@RequiredArgsConstructor
public class MockInterviewReviewServiceImpl implements MockInterviewReviewService {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_RULE = "RULE";

    private final MockInterviewReviewRecordMapper reviewMapper;
    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final ObjectMapper objectMapper;

    /**
     * 生成模拟面试复盘报告。
     */
    @Override
    public MockInterviewReviewVO generateReview(Long userId, Long sessionId) {
        /*
         * 1. 查询并校验会话归属。
         */
        MockInterviewSession session = sessionMapper.selectById(sessionId);

        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException("模拟面试会话不存在或无权限访问");
        }

        /*
         * 2. 查询本轮所有回答。
         */
        List<MockInterviewAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<MockInterviewAnswer>()
                        .eq(MockInterviewAnswer::getSessionId, sessionId)
                        .eq(MockInterviewAnswer::getUserId, userId)
                        .orderByAsc(MockInterviewAnswer::getCreateTime)
        );

        if (answers.isEmpty()) {
            throw new BizException("当前模拟面试还没有回答记录，无法生成复盘");
        }

        /*
         * 3. 查询题目，方便生成薄弱题目列表。
         */
        List<MockInterviewQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getSessionId, sessionId)
                        .eq(MockInterviewQuestion::getUserId, userId)
        );

        Map<Long, MockInterviewQuestion> questionMap = new HashMap<>();

        for (MockInterviewQuestion question : questions) {
            questionMap.put(question.getId(), question);
        }

        /*
         * 4. 计算总分、薄弱题、能力标签。
         */
        BigDecimal totalScore = calculateAverageScore(answers);
        String reviewLevel = resolveLevel(totalScore);
        List<String> weakQuestions = buildWeakQuestions(answers, questionMap);
        List<String> abilityTags = buildAbilityTags(answers);

        /*
         * 5. 生成优势、短板和提升计划。
         */
        String strengthSummary = buildStrengthSummary(totalScore, answers);
        String weaknessSummary = buildWeaknessSummary(totalScore, weakQuestions, answers);
        String improvementPlan = buildImprovementPlan(totalScore, abilityTags, weakQuestions);

        /*
         * 6. 保存复盘报告。
         */
        MockInterviewReviewRecord record = new MockInterviewReviewRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setApplicationId(session.getApplicationId());
        record.setJobId(session.getJobId());
        record.setJobTitle(session.getJobTitle());
        record.setCompanyName(session.getCompanyName());
        record.setTotalScore(totalScore);
        record.setReviewLevel(reviewLevel);
        record.setAnsweredCount(answers.size());
        record.setStrengthSummary(strengthSummary);
        record.setWeaknessSummary(weaknessSummary);
        record.setImprovementPlan(improvementPlan);
        record.setWeakQuestions(toJson(weakQuestions));
        record.setAbilityTags(toJson(abilityTags));
        record.setScoreDetailJson(toJson(buildScoreDetail(answers)));
        record.setSource(SOURCE_RULE);
        record.setIsDeleted(NOT_DELETED);

        reviewMapper.insert(record);

        return MockInterviewReviewVO.from(record, objectMapper);
    }

    /**
     * 查询最近一次复盘报告。
     */
    @Override
    public MockInterviewReviewVO getLatestReview(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);

        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException("模拟面试会话不存在或无权限访问");
        }

        MockInterviewReviewRecord record = reviewMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewReviewRecord>()
                        .eq(MockInterviewReviewRecord::getUserId, userId)
                        .eq(MockInterviewReviewRecord::getSessionId, sessionId)
                        .orderByDesc(MockInterviewReviewRecord::getCreateTime)
                        .last("limit 1")
        );

        return MockInterviewReviewVO.from(record, objectMapper);
    }

    /**
     * 计算平均分。
     */
    private BigDecimal calculateAverageScore(List<MockInterviewAnswer> answers) {
        BigDecimal sum = answers.stream()
                .map(MockInterviewAnswer::getScore)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(answers.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    /**
     * 生成薄弱题目列表。
     * 规则：低于70分的题目认为是薄弱题。
     */
    private List<String> buildWeakQuestions(
            List<MockInterviewAnswer> answers,
            Map<Long, MockInterviewQuestion> questionMap
    ) {
        List<String> weakList = new ArrayList<>();

        for (MockInterviewAnswer answer : answers) {
            if (answer.getScore() != null && answer.getScore().doubleValue() < 70) {
                MockInterviewQuestion question = questionMap.get(answer.getQuestionId());

                if (question != null) {
                    weakList.add(question.getQuestionContent() + "（得分：" + answer.getScore() + "）");
                }
            }
        }

        if (weakList.isEmpty()) {
            weakList.add("本轮没有明显低分题，但仍建议继续强化项目细节和技术原理表达。");
        }

        return weakList;
    }

    /**
     * 生成能力标签。
     */
    private List<String> buildAbilityTags(List<MockInterviewAnswer> answers) {
        List<String> tags = new ArrayList<>();

        boolean hasShortAnswerProblem = false;
        boolean hasStructureProblem = false;
        boolean hasTechKeywordProblem = false;
        boolean hasQuantifyProblem = false;

        for (MockInterviewAnswer answer : answers) {
            String problems = safe(answer.getProblems());
            String suggestions = safe(answer.getSuggestions());

            if (problems.contains("偏短") || suggestions.contains("回答偏短")) {
                hasShortAnswerProblem = true;
            }

            if (problems.contains("结构") || suggestions.contains("结构")) {
                hasStructureProblem = true;
            }

            if (problems.contains("技术关键词") || suggestions.contains("技术关键词")) {
                hasTechKeywordProblem = true;
            }

            if (suggestions.contains("量化") || suggestions.contains("结果")) {
                hasQuantifyProblem = true;
            }
        }

        if (hasShortAnswerProblem) {
            tags.add("回答完整度不足");
        }

        if (hasStructureProblem) {
            tags.add("结构化表达不足");
        }

        if (hasTechKeywordProblem) {
            tags.add("技术关键词表达不足");
        }

        if (hasQuantifyProblem) {
            tags.add("量化结果不足");
        }

        if (tags.isEmpty()) {
            tags.add("整体表达较稳定");
        }

        return tags;
    }

    /**
     * 生成优势总结。
     */
    private String buildStrengthSummary(BigDecimal totalScore, List<MockInterviewAnswer> answers) {
        double score = totalScore.doubleValue();

        if (score >= 85) {
            return "本轮模拟面试整体表现较好，回答完整度、结构化表达和技术表达都比较稳定。";
        }

        if (score >= 70) {
            return "本轮模拟面试表现中等偏上，能够回答大部分问题，并具备一定项目表达能力。";
        }

        if (score >= 60) {
            return "本轮模拟面试已经覆盖了部分问题方向，但回答深度和结构化表达还需要加强。";
        }

        return "本轮模拟面试暴露出较多基础表达问题，建议先整理项目话术和常见技术问题答案。";
    }

    /**
     * 生成短板总结。
     */
    private String buildWeaknessSummary(
            BigDecimal totalScore,
            List<String> weakQuestions,
            List<MockInterviewAnswer> answers
    ) {
        if (totalScore.doubleValue() >= 85) {
            return "本轮没有明显短板，后续可以继续提升回答的业务结果、量化指标和技术深度。";
        }

        return "本轮主要短板集中在回答完整度、表达结构、技术关键词和项目结果描述上。薄弱题数量："
                + weakQuestions.size()
                + "。";
    }

    /**
     * 生成提升计划。
     */
    private String buildImprovementPlan(
            BigDecimal totalScore,
            List<String> abilityTags,
            List<String> weakQuestions
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("建议按照以下步骤提升：\n");

        builder.append("1. 重新整理简历项目，使用“背景-任务-方案-职责-结果”的结构准备话术。\n");

        if (abilityTags.contains("技术关键词表达不足")) {
            builder.append("2. 针对岗位技能关键词，补充 Java、Spring、MySQL、Redis 等技术原理和项目使用场景。\n");
        } else {
            builder.append("2. 继续强化岗位技能关键词与项目场景之间的关联表达。\n");
        }

        if (abilityTags.contains("量化结果不足")) {
            builder.append("3. 给项目经历补充量化结果，例如响应时间、并发量、性能提升比例或业务指标。\n");
        } else {
            builder.append("3. 每个项目至少准备一个可量化的结果描述，增强说服力。\n");
        }

        builder.append("4. 针对薄弱题进行二次模拟练习，直到回答能在 1-2 分钟内完整表达。\n");

        return builder.toString();
    }

    /**
     * 构造分数明细。
     */
    private Map<String, Object> buildScoreDetail(List<MockInterviewAnswer> answers) {
        Map<String, Object> detail = new LinkedHashMap<>();

        detail.put("answerCount", answers.size());
        detail.put("scores", answers.stream()
                .map(MockInterviewAnswer::getScore)
                .toList());

        return detail;
    }

    /**
     * 分数等级。
     */
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
