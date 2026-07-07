package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.MockInterviewAnswerMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.mapper.MockInterviewReviewRecordMapper;
import com.job.bootstrap.mapper.MockInterviewSessionMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.enums.MockInterviewErrorCode;
import com.job.common.entity.interview.MockInterviewAnswer;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.interview.MockInterviewReviewRecord;
import com.job.common.entity.interview.MockInterviewSession;
import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewStudyPlanVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final String SOURCE_LLM = "LLM";
    private static final String AI_SCENE_MOCK_INTERVIEW_REVIEW_GENERATE = "MOCK_INTERVIEW_REVIEW_GENERATE";

    private final MockInterviewReviewRecordMapper reviewMapper;
    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewQuestionMapper questionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final AiModelGatewayService aiModelGatewayService;
    private final RagRetrievalService ragRetrievalService;
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
            throw mockInterviewException(MockInterviewErrorCode.SESSION_NOT_FOUND, "模拟面试会话不存在或无权限访问");
        }

        /*
         * 2. 查询本轮所有回答。
         */
        List<MockInterviewAnswer> answers = queryReviewAnswers(sessionId, userId);

        if (answers.isEmpty()) {
            throw mockInterviewException(MockInterviewErrorCode.REVIEW_NO_ANSWER, "当前模拟面试还没有回答记录，无法生成复盘");
        }

        /*
         * 3. 查询题目，方便生成薄弱题目列表。
         */
        List<MockInterviewQuestion> questions = queryReviewQuestions(sessionId, userId);

        Map<Long, MockInterviewQuestion> questionMap = new HashMap<>();

        for (MockInterviewQuestion question : questions) {
            questionMap.put(question.getId(), question);
        }

        /*
         * 4. 把每道题、用户回答和单题评分交给模型生成总体复盘。
         *    这里不再使用规则拼接兜底，因为用户要求复盘必须是真实 AI 生成。
         */
        LlmReviewResult llmResult = generateLlmReview(userId, session, answers, questionMap);

        /*
         * 5. 保存模型复盘报告。
         */
        MockInterviewReviewRecord record = new MockInterviewReviewRecord();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setApplicationId(session.getApplicationId());
        record.setJobId(session.getJobId());
        record.setJobTitle(session.getJobTitle());
        record.setCompanyName(session.getCompanyName());
        record.setTotalScore(toScore(llmResult.totalScore()));
        record.setReviewLevel(trimOrDefault(llmResult.reviewLevel(), resolveLevel(toScore(llmResult.totalScore()))));
        record.setAnsweredCount(answers.size());
        record.setStrengthSummary(trimOrDefault(llmResult.strengthSummary(), "AI 未返回优势总结。"));
        record.setWeaknessSummary(trimOrDefault(llmResult.weaknessSummary(), "AI 未返回短板总结。"));
        record.setImprovementPlan(trimOrDefault(llmResult.improvementPlan(), "AI 未返回提升计划。"));
        record.setWeakQuestions(toJson(limitStringList(llmResult.weakQuestions())));
        record.setAbilityTags(toJson(limitStringList(llmResult.abilityTags())));
        record.setScoreDetailJson(toJson(buildScoreDetail(answers)));
        record.setSource(SOURCE_LLM);
        record.setIsDeleted(NOT_DELETED);

        reviewMapper.insert(record);

        return toReviewVO(record, questions, answers);
    }

    @Override
    public MockInterviewStudyPlanVO buildStudyPlan(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw mockInterviewException(MockInterviewErrorCode.SESSION_NOT_FOUND, "模拟面试会话不存在或无权限访问");
        }

        MockInterviewReviewRecord review = reviewMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewReviewRecord>()
                        .eq(MockInterviewReviewRecord::getUserId, userId)
                        .eq(MockInterviewReviewRecord::getSessionId, sessionId)
                        .eq(MockInterviewReviewRecord::getIsDeleted, NOT_DELETED)
                        .orderByDesc(MockInterviewReviewRecord::getCreateTime)
                        .last("limit 1")
        );
        if (review == null) {
            throw mockInterviewException(MockInterviewErrorCode.STUDY_PLAN_REVIEW_REQUIRED, "请先生成 AI 面试总结，再查看补课清单");
        }

        List<String> knowledgePoints = buildStudyKnowledgePoints(review);
        MockInterviewStudyPlanVO plan = new MockInterviewStudyPlanVO();
        plan.setSessionId(sessionId);
        plan.setReviewId(review.getId());

        for (String knowledgePoint : knowledgePoints) {
            MockInterviewStudyPlanVO.StudyItem item = new MockInterviewStudyPlanVO.StudyItem();
            item.setKnowledgePoint(knowledgePoint);
            item.setSuggestion(buildStudySuggestion(knowledgePoint, review.getImprovementPlan()));
            item.setMaterials(searchStudyMaterials(userId, session, knowledgePoint));
            plan.getItems().add(item);
        }
        return plan;
    }

    private List<String> buildStudyKnowledgePoints(MockInterviewReviewRecord review) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        points.addAll(readStringList(review.getWeakQuestions()));
        points.addAll(readStringList(review.getAbilityTags()));

        /*
         * 补课清单第一版最多展示 6 个知识点，避免一次复盘后页面过长。
         */
        return points.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(6)
                .toList();
    }

    private String buildStudySuggestion(String knowledgePoint, String improvementPlan) {
        if (StringUtils.hasText(improvementPlan)) {
            return "围绕“" + knowledgePoint + "”复习，并结合 AI 提升计划练习：" + improvementPlan;
        }
        return "围绕“" + knowledgePoint + "”补充基础概念、常见面试问法和项目中的真实使用场景。";
    }

    private List<MockInterviewStudyPlanVO.StudyMaterial> searchStudyMaterials(
            Long userId,
            MockInterviewSession session,
            String knowledgePoint
    ) {
        String query = safe(session.getJobTitle()) + " " + knowledgePoint + " 面试 标准答案 知识点";
        List<RagSearchResultVO> results;
        try {
            results = ragRetrievalService.search(userId, query, 3);
        } catch (Exception exception) {
            throw new BizException(
                    MockInterviewErrorCode.STUDY_MATERIAL_FAILED.getCode(),
                    "补课材料加载失败: " + exception.getMessage(),
                    exception
            );
        }
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream()
                .filter(result -> StringUtils.hasText(result.getContent()))
                .map(this::toStudyMaterial)
                .limit(3)
                .toList();
    }

    private MockInterviewStudyPlanVO.StudyMaterial toStudyMaterial(RagSearchResultVO result) {
        MockInterviewStudyPlanVO.StudyMaterial material = new MockInterviewStudyPlanVO.StudyMaterial();
        material.setDocumentId(result.getDocumentId());
        material.setChunkId(result.getChunkId());
        material.setTitle(StringUtils.hasText(result.getTitle()) ? result.getTitle() : result.getReferenceTitle());
        material.setContent(truncate(result.getContent(), 360));
        material.setSource(result.getSource());
        material.setScore(result.getScore());
        return material;
    }

    /**
     * 查询最近一次复盘报告。
     */
    @Override
    public MockInterviewReviewVO getLatestReview(Long userId, Long sessionId) {
        MockInterviewSession session = sessionMapper.selectById(sessionId);

        if (session == null || !userId.equals(session.getUserId())) {
            throw mockInterviewException(MockInterviewErrorCode.SESSION_NOT_FOUND, "模拟面试会话不存在或无权限访问");
        }

        MockInterviewReviewRecord record = reviewMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewReviewRecord>()
                        .eq(MockInterviewReviewRecord::getUserId, userId)
                        .eq(MockInterviewReviewRecord::getSessionId, sessionId)
                        .eq(MockInterviewReviewRecord::getIsDeleted, NOT_DELETED)
                        .orderByDesc(MockInterviewReviewRecord::getCreateTime)
                        .last("limit 1")
        );

        if (record == null) {
            return null;
        }

        List<MockInterviewQuestion> questions = queryReviewQuestions(sessionId, userId);
        List<MockInterviewAnswer> answers = queryReviewAnswers(sessionId, userId);
        return toReviewVO(record, questions, answers);
    }

    /**
     * 组装复盘详情 VO。
     *
     * 步骤:
     * 1. 先把 mock_interview_review_record 转成总体复盘信息。
     * 2. 再按 questionId 把用户回答整理成 Map，方便和题目一一匹配。
     * 3. 最后按题目顺序生成单题复盘列表，保证用户端和 admin 端展示口径一致。
     */
    private MockInterviewReviewVO toReviewVO(
            MockInterviewReviewRecord record,
            List<MockInterviewQuestion> questions,
            List<MockInterviewAnswer> answers
    ) {
        MockInterviewReviewVO vo = MockInterviewReviewVO.from(record, objectMapper);
        if (vo == null) {
            return null;
        }

        Map<Long, MockInterviewAnswer> answerMap = new HashMap<>();
        for (MockInterviewAnswer answer : answers) {
            answerMap.put(answer.getQuestionId(), answer);
        }

        List<MockInterviewReviewVO.QuestionReviewItem> questionReviews = new ArrayList<>();
        for (MockInterviewQuestion question : questions) {
            MockInterviewAnswer answer = answerMap.get(question.getId());
            questionReviews.add(toQuestionReviewItem(question, answer));
        }
        vo.setQuestionReviews(questionReviews);
        return vo;
    }

    /**
     * 组装单题复盘明细。
     *
     * 步骤:
     * 1. 题目基础信息来自 mock_interview_question，保留面试当时的标准答案快照。
     * 2. 用户回答和评分字段来自 mock_interview_answer，复用提交答案时已经生成的单题评价。
     * 3. 多行文本字段统一拆成列表，前端可以直接渲染标签和要点。
     */
    private MockInterviewReviewVO.QuestionReviewItem toQuestionReviewItem(
            MockInterviewQuestion question,
            MockInterviewAnswer answer
    ) {
        MockInterviewReviewVO.QuestionReviewItem item = new MockInterviewReviewVO.QuestionReviewItem();
        item.setQuestionId(question.getId());
        item.setSortNo(question.getSortNo());
        item.setQuestionType(question.getQuestionType());
        item.setQuestionContent(question.getQuestionContent());
        item.setStandardAnswer(question.getStandardAnswer());

        if (answer != null) {
            item.setAnswerId(answer.getId());
            item.setUserAnswer(answer.getAnswerContent());
            item.setScore(answer.getScore());
            item.setLevel(answer.getLevel());
            item.setCorrect(answer.getCorrectFlag() != null && answer.getCorrectFlag() == 1);
            item.setSimilarityScore(answer.getSimilarityScore());
            item.setMatchedPoints(splitLines(answer.getMatchedPoints()));
            item.setMissingPoints(splitLines(answer.getMissingPoints()));
            item.setKnowledgePoints(splitLines(answer.getKnowledgePoints()));
            item.setReviewConclusion(answer.getReviewConclusion());
            item.setStrengths(splitLines(answer.getStrengths()));
            item.setProblems(splitLines(answer.getProblems()));
            item.setSuggestions(splitLines(answer.getSuggestions()));
            item.setWrongBook(answer.getWrongBookFlag() != null && answer.getWrongBookFlag() == 1);
        }
        return item;
    }

    /**
     * 查询复盘需要展示的题目列表。
     *
     * 步骤:
     * 1. 只查询当前用户、当前会话的数据，避免后台或接口误串用户数据。
     * 2. 排除逻辑删除数据。
     * 3. 按 sortNo 升序返回，保证复盘顺序和面试顺序一致。
     */
    private List<MockInterviewQuestion> queryReviewQuestions(Long sessionId, Long userId) {
        return questionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewQuestion>()
                        .eq(MockInterviewQuestion::getSessionId, sessionId)
                        .eq(MockInterviewQuestion::getUserId, userId)
                        .eq(MockInterviewQuestion::getIsDeleted, NOT_DELETED)
                        .orderByAsc(MockInterviewQuestion::getSortNo)
        );
    }

    /**
     * 查询复盘需要展示的回答列表。
     *
     * 步骤:
     * 1. 只查询当前用户、当前会话的回答。
     * 2. 排除逻辑删除数据。
     * 3. 按创建时间升序返回，便于后续排查回答提交顺序。
     */
    private List<MockInterviewAnswer> queryReviewAnswers(Long sessionId, Long userId) {
        return answerMapper.selectList(
                new LambdaQueryWrapper<MockInterviewAnswer>()
                        .eq(MockInterviewAnswer::getSessionId, sessionId)
                        .eq(MockInterviewAnswer::getUserId, userId)
                        .eq(MockInterviewAnswer::getIsDeleted, NOT_DELETED)
                        .orderByAsc(MockInterviewAnswer::getCreateTime)
        );
    }

    /**
     * 多行文本转列表。
     *
     * 说明:
     * 单题评价中的命中点、缺失点、知识点早期按多行文本保存。
     * 这里集中拆分，避免用户端和 admin 端重复写解析逻辑。
     */
    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private LlmReviewResult generateLlmReview(
            Long userId,
            MockInterviewSession session,
            List<MockInterviewAnswer> answers,
            Map<Long, MockInterviewQuestion> questionMap
    ) {
        try {
            /*
             * 1. 模型调用和 JSON 解析放在同一个 try 内，确保复盘阶段所有失败都能返回稳定错误码。
             * 2. JSON 格式错误单独标记 REVIEW_JSON_PARSE_FAILED，方便前端提示检查 Prompt 输出。
             * 3. 其他模型调用异常统一标记 REVIEW_GENERATE_FAILED，方便和 ASR/评分失败区分。
             */
            Map<String, Object> variables = buildReviewVariables(session, answers, questionMap);
            String prompt = buildReviewPrompt(session, answers, questionMap);
            String response = aiModelGatewayService.chat(
                    AI_SCENE_MOCK_INTERVIEW_REVIEW_GENERATE,
                    variables,
                    prompt,
                    userId,
                    buildReviewTraceId(userId, session.getId())
            );
            LlmReviewResult result = objectMapper.readValue(extractJson(response), LlmReviewResult.class);
            validateLlmReviewResult(result);
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(
                    MockInterviewErrorCode.REVIEW_GENERATE_FAILED.getCode(),
                    "AI复盘生成失败，请稍后重试",
                    exception
            );
        }
    }

    private Map<String, Object> buildReviewVariables(
            MockInterviewSession session,
            List<MockInterviewAnswer> answers,
            Map<Long, MockInterviewQuestion> questionMap
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("jobTitle", session.getJobTitle());
        variables.put("job_title", session.getJobTitle());
        variables.put("companyName", session.getCompanyName());
        variables.put("company_name", session.getCompanyName());
        variables.put("answeredCount", answers.size());
        variables.put("answered_count", answers.size());
        variables.put("averageScore", calculateAverageScore(answers));
        variables.put("average_score", variables.get("averageScore"));
        variables.put("answerDetails", buildAnswerDetails(answers, questionMap));
        variables.put("answer_details", variables.get("answerDetails"));
        variables.put("jsonFormat", "只输出 JSON 对象，不要 Markdown，不要解释文本。");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildReviewPrompt(
            MockInterviewSession session,
            List<MockInterviewAnswer> answers,
            Map<Long, MockInterviewQuestion> questionMap
    ) {
        StringBuilder detailBuilder = new StringBuilder();
        int index = 1;
        for (MockInterviewAnswer answer : answers) {
            MockInterviewQuestion question = questionMap.get(answer.getQuestionId());
            detailBuilder.append("题目 ").append(index++).append(":\n");
            detailBuilder.append("题目内容: ").append(question == null ? "" : safe(question.getQuestionContent())).append('\n');
            detailBuilder.append("标准答案: ").append(question == null ? "" : safe(question.getStandardAnswer())).append('\n');
            detailBuilder.append("用户回答: ").append(safe(answer.getAnswerContent())).append('\n');
            detailBuilder.append("单题得分: ").append(answer.getScore()).append('\n');
            detailBuilder.append("单题等级: ").append(safe(answer.getLevel())).append('\n');
            detailBuilder.append("单题优点: ").append(safe(answer.getStrengths())).append('\n');
            detailBuilder.append("单题问题: ").append(safe(answer.getProblems())).append('\n');
            detailBuilder.append("单题建议: ").append(safe(answer.getSuggestions())).append("\n\n");
        }

        return """
                请你作为资深技术面试官，基于本轮模拟面试所有题目、标准答案、用户回答和单题评分，生成真实的总体复盘。
                
                要求:
                1. totalScore 为 0-100 的数字，综合单题表现给出。
                2. reviewLevel 只能是 优秀、良好、一般、待提升。
                3. strengthSummary 总结用户本轮表现中的优势，不要空泛。
                4. weaknessSummary 总结主要短板，要结合用户回答和题目。
                5. abilityTags 输出 2-6 个能力标签。
                6. weakQuestions 输出 1-5 个薄弱题目或薄弱知识点。
                7. improvementPlan 要明确告诉用户接下来需要补充什么知识、怎么练。
                8. 只输出 JSON 对象，不要 Markdown，不要解释文本。
                
                JSON 格式:
                {
                  "totalScore": 82,
                  "reviewLevel": "良好",
                  "strengthSummary": "优势总结",
                  "weaknessSummary": "短板总结",
                  "abilityTags": ["标签1", "标签2"],
                  "weakQuestions": ["薄弱题或知识点1"],
                  "improvementPlan": "提升计划"
                }
                
                岗位: %s
                公司: %s
                已回答题数: %d
                
                面试明细:
                %s
                """.formatted(
                safe(session.getJobTitle()),
                safe(session.getCompanyName()),
                answers.size(),
                detailBuilder
        );
    }

    private List<Map<String, Object>> buildAnswerDetails(
            List<MockInterviewAnswer> answers,
            Map<Long, MockInterviewQuestion> questionMap
    ) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (MockInterviewAnswer answer : answers) {
            MockInterviewQuestion question = questionMap.get(answer.getQuestionId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question", question == null ? "" : question.getQuestionContent());
            item.put("standardAnswer", question == null ? "" : question.getStandardAnswer());
            item.put("userAnswer", answer.getAnswerContent());
            item.put("score", answer.getScore());
            item.put("level", answer.getLevel());
            item.put("strengths", answer.getStrengths());
            item.put("problems", answer.getProblems());
            item.put("suggestions", answer.getSuggestions());
            details.add(item);
        }
        return details;
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw mockInterviewException(MockInterviewErrorCode.REVIEW_JSON_PARSE_FAILED, "AI复盘结果解析失败，请稍后重试");
        }

        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw mockInterviewException(MockInterviewErrorCode.REVIEW_JSON_PARSE_FAILED, "AI复盘结果解析失败，请稍后重试");
        }
        return cleaned.substring(start, end + 1);
    }

    private void validateLlmReviewResult(LlmReviewResult result) {
        if (result == null
                || result.totalScore() == null
                || !StringUtils.hasText(result.reviewLevel())
                || !StringUtils.hasText(result.strengthSummary())
                || !StringUtils.hasText(result.weaknessSummary())
                || !StringUtils.hasText(result.improvementPlan())) {
            throw mockInterviewException(MockInterviewErrorCode.REVIEW_JSON_PARSE_FAILED, "AI复盘结果解析失败，请稍后重试");
        }
    }

    /**
     * 创建 AI 面试复盘标准业务异常。
     *
     * 步骤:
     * 1. 使用 MockInterviewErrorCode 中的稳定错误码。
     * 2. message 保留用户可读提示，兼容旧的通用错误弹窗。
     * 3. 前端可以通过 data.errorCode 精准判断复盘、RAG 补课材料等失败场景。
     */
    private BizException mockInterviewException(MockInterviewErrorCode errorCode, String message) {
        return new BizException(errorCode.getCode(), trimOrDefault(message, errorCode.getDefaultMessage()));
    }

    private BigDecimal toScore(Double score) {
        double value = score == null ? 0 : Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> limitStringList(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(6)
                .toList();
    }

    private String trimOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String buildReviewTraceId(Long userId, Long sessionId) {
        return "mock_review_"
                + userId
                + "_"
                + sessionId
                + "_"
                + UUID.randomUUID();
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

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private record LlmReviewResult(
            Double totalScore,
            String reviewLevel,
            String strengthSummary,
            String weaknessSummary,
            List<String> abilityTags,
            List<String> weakQuestions,
            String improvementPlan
    ) {
    }
}
