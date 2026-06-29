package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanItemMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanMapper;
import com.job.bootstrap.mapper.MockInterviewStudyPlanRetestMapper;
import com.job.bootstrap.mapper.MockInterviewWrongQuestionMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.MockInterviewLearningPlanService;
import com.job.common.dto.interview.MockInterviewStudyPlanGenerateDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanItemStatusDTO;
import com.job.common.dto.interview.MockInterviewStudyPlanRetestSubmitDTO;
import com.job.common.entity.interview.MockInterviewStudyPlan;
import com.job.common.entity.interview.MockInterviewStudyPlanItem;
import com.job.common.entity.interview.MockInterviewStudyPlanRetest;
import com.job.common.entity.interview.MockInterviewWrongQuestion;
import com.job.common.vo.interview.MockInterviewLearningPlanVO;
import com.job.common.vo.interview.MockInterviewStudyPlanRetestVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模拟面试学习计划服务实现。
 */
@Service
@RequiredArgsConstructor
public class MockInterviewLearningPlanServiceImpl implements MockInterviewLearningPlanService {

    private static final int NOT_DELETED = 0;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String ITEM_PENDING = "PENDING";
    private static final String ITEM_DONE = "DONE";
    private static final String SOURCE_WRONG_QUESTION = "WRONG_QUESTION";
    private static final String SCENE_STUDY_PLAN_GENERATE = "MOCK_INTERVIEW_STUDY_PLAN_GENERATE";
    private static final String SCENE_STUDY_PLAN_RETEST_EVALUATE = "MOCK_INTERVIEW_STUDY_PLAN_RETEST_EVALUATE";
    private static final String RETEST_PENDING = "PENDING";
    private static final String RETEST_SUBMITTED = "SUBMITTED";

    private final MockInterviewStudyPlanMapper planMapper;
    private final MockInterviewStudyPlanItemMapper itemMapper;
    private final MockInterviewStudyPlanRetestMapper retestMapper;
    private final MockInterviewWrongQuestionMapper wrongQuestionMapper;
    private final RagRetrievalService ragRetrievalService;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewLearningPlanVO generatePlan(Long userId, MockInterviewStudyPlanGenerateDTO dto) {
        int planDays = normalizePlanDays(dto.getPlanDays());
        List<MockInterviewWrongQuestion> wrongQuestions = loadActiveWrongQuestions(userId);
        if (wrongQuestions.isEmpty()) {
            throw new BizException("暂无未掌握或复习中的错题，完成 AI 面试后再生成学习计划");
        }

        List<String> knowledgePoints = extractKnowledgePoints(wrongQuestions, planDays);
        Map<String, List<MockInterviewLearningPlanVO.Material>> materialMap = buildMaterialMap(userId, knowledgePoints);
        List<PlanItemSeed> seeds = generatePlanSeeds(userId, planDays, knowledgePoints, wrongQuestions, materialMap);

        MockInterviewStudyPlan plan = new MockInterviewStudyPlan();
        plan.setUserId(userId);
        plan.setPlanTitle(planDays + " 天 AI 面试提升计划");
        plan.setPlanDays(planDays);
        plan.setSource(SOURCE_WRONG_QUESTION);
        plan.setWeakKnowledgePoints(String.join("\n", knowledgePoints));
        plan.setStatus(STATUS_ACTIVE);
        plan.setIsDeleted(NOT_DELETED);
        planMapper.insert(plan);

        for (PlanItemSeed seed : seeds) {
            MockInterviewStudyPlanItem item = new MockInterviewStudyPlanItem();
            item.setPlanId(plan.getId());
            item.setUserId(userId);
            item.setDayNo(seed.dayNo());
            item.setTitle(seed.title());
            item.setKnowledgePoint(seed.knowledgePoint());
            item.setLearningGoal(seed.learningGoal());
            item.setPracticeTask(seed.practiceTask());
            item.setReviewSuggestion(seed.reviewSuggestion());
            item.setMaterialsJson(toJson(materialMap.getOrDefault(seed.knowledgePoint(), Collections.emptyList())));
            item.setCompletionStatus(ITEM_PENDING);
            item.setIsDeleted(NOT_DELETED);
            itemMapper.insert(item);
        }

        return getPlanDetail(userId, plan.getId());
    }

    @Override
    public MockInterviewLearningPlanVO getLatestPlan(Long userId) {
        MockInterviewStudyPlan plan = planMapper.selectOne(
                new LambdaQueryWrapper<MockInterviewStudyPlan>()
                        .eq(MockInterviewStudyPlan::getUserId, userId)
                        .eq(MockInterviewStudyPlan::getIsDeleted, NOT_DELETED)
                        .orderByDesc(MockInterviewStudyPlan::getCreateTime)
                        .last("limit 1")
        );
        if (plan == null) {
            return null;
        }
        return getPlanDetail(userId, plan.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewLearningPlanVO updateItemStatus(Long userId, Long itemId, MockInterviewStudyPlanItemStatusDTO dto) {
        MockInterviewStudyPlanItem item = itemMapper.selectById(itemId);
        if (item == null || !userId.equals(item.getUserId()) || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
            throw new BizException("学习任务不存在或无权限访问");
        }

        item.setCompletionStatus(normalizeItemStatus(dto.getCompletionStatus()));
        itemMapper.updateById(item);
        refreshPlanStatus(item.getPlanId(), userId);
        return getPlanDetail(userId, item.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewStudyPlanRetestVO startRetest(Long userId, Long itemId) {
        MockInterviewStudyPlanItem item = getUserPlanItemRequired(userId, itemId);
        MockInterviewStudyPlan plan = getUserPlanRequired(userId, item.getPlanId());

        MockInterviewStudyPlanRetest retest = new MockInterviewStudyPlanRetest();
        retest.setUserId(userId);
        retest.setPlanId(plan.getId());
        retest.setItemId(item.getId());
        retest.setKnowledgePoint(item.getKnowledgePoint());
        retest.setQuestionContent(buildRetestQuestion(item));
        retest.setStandardAnswer(buildRetestStandardAnswer(item));
        retest.setStatus(RETEST_PENDING);
        retest.setIsDeleted(NOT_DELETED);
        retestMapper.insert(retest);
        return MockInterviewStudyPlanRetestVO.from(retest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockInterviewStudyPlanRetestVO submitRetest(Long userId, Long retestId, MockInterviewStudyPlanRetestSubmitDTO dto) {
        MockInterviewStudyPlanRetest retest = retestMapper.selectById(retestId);
        if (retest == null || !userId.equals(retest.getUserId()) || (retest.getIsDeleted() != null && retest.getIsDeleted() == 1)) {
            throw new BizException("复测记录不存在或无权限访问");
        }
        if (RETEST_SUBMITTED.equals(retest.getStatus())) {
            throw new BizException("该复测已经提交过");
        }

        RetestEvaluation evaluation = evaluateRetest(userId, retest, dto.getUserAnswer());
        retest.setUserAnswer(dto.getUserAnswer());
        retest.setScore(evaluation.score());
        retest.setPassedFlag(evaluation.passed() ? 1 : 0);
        retest.setFeedback(evaluation.feedback());
        retest.setSuggestion(evaluation.suggestion());
        retest.setStatus(RETEST_SUBMITTED);
        retestMapper.updateById(retest);

        updateWrongQuestionsAfterRetest(userId, retest.getKnowledgePoint(), evaluation.passed());
        return MockInterviewStudyPlanRetestVO.from(retest);
    }

    private MockInterviewLearningPlanVO getPlanDetail(Long userId, Long planId) {
        MockInterviewStudyPlan plan = planMapper.selectById(planId);
        if (plan == null || !userId.equals(plan.getUserId())) {
            throw new BizException("学习计划不存在或无权限访问");
        }

        List<MockInterviewStudyPlanItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MockInterviewStudyPlanItem>()
                        .eq(MockInterviewStudyPlanItem::getPlanId, planId)
                        .eq(MockInterviewStudyPlanItem::getUserId, userId)
                        .eq(MockInterviewStudyPlanItem::getIsDeleted, NOT_DELETED)
                        .orderByAsc(MockInterviewStudyPlanItem::getDayNo)
        );
        return MockInterviewLearningPlanVO.from(plan, items, objectMapper);
    }

    private MockInterviewStudyPlan getUserPlanRequired(Long userId, Long planId) {
        MockInterviewStudyPlan plan = planMapper.selectById(planId);
        if (plan == null || !userId.equals(plan.getUserId()) || (plan.getIsDeleted() != null && plan.getIsDeleted() == 1)) {
            throw new BizException("学习计划不存在或无权限访问");
        }
        return plan;
    }

    private MockInterviewStudyPlanItem getUserPlanItemRequired(Long userId, Long itemId) {
        MockInterviewStudyPlanItem item = itemMapper.selectById(itemId);
        if (item == null || !userId.equals(item.getUserId()) || (item.getIsDeleted() != null && item.getIsDeleted() == 1)) {
            throw new BizException("学习任务不存在或无权限访问");
        }
        return item;
    }

    private String buildRetestQuestion(MockInterviewStudyPlanItem item) {
        return "请围绕“" + item.getKnowledgePoint() + "”回答："
                + "它的核心概念是什么？常见面试追问有哪些？你会如何结合项目经历说明自己掌握了这个知识点？";
    }

    private String buildRetestStandardAnswer(MockInterviewStudyPlanItem item) {
        List<MockInterviewLearningPlanVO.Material> materials = MockInterviewLearningPlanVO.Item
                .from(item, objectMapper)
                .getMaterials();
        String materialText = materials.stream()
                .map(MockInterviewLearningPlanVO.Material::getContent)
                .filter(StringUtils::hasText)
                .limit(2)
                .reduce("", (left, right) -> left + "\n" + right);
        return String.join("\n",
                "知识点：" + item.getKnowledgePoint(),
                "学习目标：" + item.getLearningGoal(),
                "练习任务：" + item.getPracticeTask(),
                "参考材料：" + truncate(materialText, 800)
        );
    }

    private RetestEvaluation evaluateRetest(Long userId, MockInterviewStudyPlanRetest retest, String userAnswer) {
        try {
            String prompt = buildRetestEvaluatePrompt(retest, userAnswer);
            String response = aiModelGatewayService.chat(
                    SCENE_STUDY_PLAN_RETEST_EVALUATE,
                    buildRetestVariables(retest, userAnswer, prompt),
                    prompt,
                    userId,
                    "mock_study_retest_" + userId + "_" + retest.getId() + "_" + UUID.randomUUID()
            );
            LlmRetestResult result = objectMapper.readValue(extractJson(response), LlmRetestResult.class);
            return normalizeRetestEvaluation(result);
        } catch (Exception ignored) {
            return fallbackRetestEvaluation(retest, userAnswer);
        }
    }

    private Map<String, Object> buildRetestVariables(
            MockInterviewStudyPlanRetest retest,
            String userAnswer,
            String prompt
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("knowledgePoint", retest.getKnowledgePoint());
        variables.put("knowledge_point", retest.getKnowledgePoint());
        variables.put("question", retest.getQuestionContent());
        variables.put("standardAnswer", retest.getStandardAnswer());
        variables.put("standard_answer", retest.getStandardAnswer());
        variables.put("userAnswer", userAnswer);
        variables.put("user_answer", userAnswer);
        variables.put("fullPrompt", prompt);
        variables.put("full_prompt", prompt);
        variables.put("jsonFormat", "只输出 JSON，不要 Markdown");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildRetestEvaluatePrompt(MockInterviewStudyPlanRetest retest, String userAnswer) {
        return """
                你是面试训练复测评分官。请判断用户是否掌握指定知识点。
                只输出 JSON，不要 Markdown。
                JSON 格式:
                {"score":80,"passed":true,"feedback":"...","suggestion":"..."}
                评分规则:
                1. score 范围 0-100。
                2. passed 表示是否达到可面试表达水平，通常 score >= 75 才通过。
                3. 重点看核心概念、常见追问、项目表达是否覆盖。
                
                知识点:
                %s
                
                复测题目:
                %s
                
                参考答案:
                %s
                
                用户回答:
                %s
                """.formatted(
                retest.getKnowledgePoint(),
                retest.getQuestionContent(),
                retest.getStandardAnswer(),
                userAnswer
        );
    }

    private RetestEvaluation normalizeRetestEvaluation(LlmRetestResult result) {
        if (result == null || result.score() == null) {
            throw new IllegalArgumentException("模型复测结果为空");
        }
        java.math.BigDecimal score = java.math.BigDecimal.valueOf(Math.max(0, Math.min(100, result.score())));
        boolean passed = result.passed() != null ? result.passed() : score.compareTo(java.math.BigDecimal.valueOf(75)) >= 0;
        return new RetestEvaluation(
                score,
                passed,
                defaultText(result.feedback(), passed ? "复测通过，回答基本达到面试表达要求。" : "复测未通过，回答仍有关键缺口。"),
                defaultText(result.suggestion(), passed ? "下一步可以进入更高难度追问。" : "建议回到学习材料补齐核心概念和项目例子。")
        );
    }

    private RetestEvaluation fallbackRetestEvaluation(MockInterviewStudyPlanRetest retest, String userAnswer) {
        int score = 45;
        String answer = userAnswer == null ? "" : userAnswer.trim();
        if (answer.length() >= 80) {
            score += 20;
        }
        if (containsIgnoreCase(answer, retest.getKnowledgePoint())) {
            score += 15;
        }
        if (answer.contains("项目") || answer.contains("场景") || answer.contains("解决") || answer.contains("结果")) {
            score += 15;
        }
        score = Math.min(score, 100);
        boolean passed = score >= 75;
        return new RetestEvaluation(
                java.math.BigDecimal.valueOf(score),
                passed,
                passed ? "规则复测通过：回答长度、知识点和项目表达基本达标。" : "规则复测未通过：回答需要补充核心概念和项目化表达。",
                passed ? "可以进入下一轮模拟面试验证稳定性。" : "建议先补充定义、原理、常见追问和项目例子后再复测。"
        );
    }

    private void updateWrongQuestionsAfterRetest(Long userId, String knowledgePoint, boolean passed) {
        if (!StringUtils.hasText(knowledgePoint)) {
            return;
        }
        List<MockInterviewWrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewWrongQuestion>()
                        .eq(MockInterviewWrongQuestion::getUserId, userId)
                        .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED)
                        .like(MockInterviewWrongQuestion::getKnowledgePoints, knowledgePoint)
        );
        for (MockInterviewWrongQuestion wrongQuestion : wrongQuestions) {
            wrongQuestion.setMasteryStatus(passed ? "MASTERED" : "REVIEWING");
            wrongQuestionMapper.updateById(wrongQuestion);
        }
    }

    private List<MockInterviewWrongQuestion> loadActiveWrongQuestions(Long userId) {
        return wrongQuestionMapper.selectList(
                new LambdaQueryWrapper<MockInterviewWrongQuestion>()
                        .eq(MockInterviewWrongQuestion::getUserId, userId)
                        .eq(MockInterviewWrongQuestion::getIsDeleted, NOT_DELETED)
                        .in(MockInterviewWrongQuestion::getMasteryStatus, "UNMASTERED", "REVIEWING")
                        .orderByDesc(MockInterviewWrongQuestion::getWrongCount)
                        .orderByDesc(MockInterviewWrongQuestion::getUpdateTime)
                        .last("limit 50")
        );
    }

    private List<String> extractKnowledgePoints(List<MockInterviewWrongQuestion> wrongQuestions, int planDays) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (MockInterviewWrongQuestion wrongQuestion : wrongQuestions) {
            splitLines(wrongQuestion.getKnowledgePoints()).forEach(points::add);
            if (points.size() >= planDays) {
                break;
            }
        }
        if (points.isEmpty()) {
            wrongQuestions.stream()
                    .map(MockInterviewWrongQuestion::getQuestionType)
                    .filter(StringUtils::hasText)
                    .forEach(points::add);
        }
        return points.stream().limit(planDays).toList();
    }

    private Map<String, List<MockInterviewLearningPlanVO.Material>> buildMaterialMap(Long userId, List<String> knowledgePoints) {
        Map<String, List<MockInterviewLearningPlanVO.Material>> materialMap = new LinkedHashMap<>();
        for (String point : knowledgePoints) {
            List<RagSearchResultVO> results;
            try {
                results = ragRetrievalService.search(userId, point + " 面试 知识点 标准答案", 3);
            } catch (Exception exception) {
                results = Collections.emptyList();
            }
            materialMap.put(point, results.stream().map(this::toMaterial).toList());
        }
        return materialMap;
    }

    private List<PlanItemSeed> generatePlanSeeds(
            Long userId,
            int planDays,
            List<String> knowledgePoints,
            List<MockInterviewWrongQuestion> wrongQuestions,
            Map<String, List<MockInterviewLearningPlanVO.Material>> materialMap
    ) {
        try {
            String prompt = buildPlanPrompt(planDays, knowledgePoints, wrongQuestions, materialMap);
            String response = aiModelGatewayService.chat(
                    SCENE_STUDY_PLAN_GENERATE,
                    buildPlanVariables(planDays, knowledgePoints, wrongQuestions, materialMap, prompt),
                    prompt,
                    userId,
                    "mock_study_plan_" + userId + "_" + UUID.randomUUID()
            );
            LlmPlanResult result = objectMapper.readValue(extractJson(response), LlmPlanResult.class);
            List<PlanItemSeed> seeds = normalizeLlmItems(result.items(), knowledgePoints, planDays);
            if (!seeds.isEmpty()) {
                return seeds;
            }
        } catch (Exception ignored) {
            // 模型路由未配置或模型输出异常时，使用规则计划兜底，保证用户能看到训练路径。
        }
        return buildFallbackSeeds(planDays, knowledgePoints);
    }

    private List<PlanItemSeed> buildFallbackSeeds(int planDays, List<String> knowledgePoints) {
        List<PlanItemSeed> seeds = new ArrayList<>();
        for (int index = 0; index < planDays; index++) {
            String point = knowledgePoints.get(index % knowledgePoints.size());
            seeds.add(new PlanItemSeed(
                    index + 1,
                    "第 " + (index + 1) + " 天：" + point + " 强化训练",
                    point,
                    "理解 " + point + " 的核心概念、常见面试问法和项目表达方式。",
                    "复盘错题中的缺失点，整理 1 段 STAR/问题-分析-解决-结果 结构化回答。",
                    "当天结束后用自己的话复述一遍，并在下一轮 AI 面试中重点验证。"
            ));
        }
        return seeds;
    }

    private List<PlanItemSeed> normalizeLlmItems(List<LlmPlanItem> items, List<String> knowledgePoints, int planDays) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<PlanItemSeed> seeds = new ArrayList<>();
        for (int index = 0; index < Math.min(items.size(), planDays); index++) {
            LlmPlanItem item = items.get(index);
            String point = StringUtils.hasText(item.knowledgePoint())
                    ? item.knowledgePoint().trim()
                    : knowledgePoints.get(index % knowledgePoints.size());
            seeds.add(new PlanItemSeed(
                    index + 1,
                    defaultText(item.title(), "第 " + (index + 1) + " 天：" + point),
                    point,
                    defaultText(item.learningGoal(), "掌握 " + point + " 的核心要点。"),
                    defaultText(item.practiceTask(), "围绕错题重新组织一段结构化回答。"),
                    defaultText(item.reviewSuggestion(), "完成后用模拟面试验证掌握情况。")
            ));
        }
        while (seeds.size() < planDays) {
            seeds.addAll(buildFallbackSeeds(planDays - seeds.size(), knowledgePoints));
        }
        return seeds.stream().limit(planDays).toList();
    }

    private Map<String, Object> buildPlanVariables(
            int planDays,
            List<String> knowledgePoints,
            List<MockInterviewWrongQuestion> wrongQuestions,
            Map<String, List<MockInterviewLearningPlanVO.Material>> materialMap,
            String prompt
    ) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("planDays", planDays);
        variables.put("plan_days", planDays);
        variables.put("knowledgePoints", knowledgePoints);
        variables.put("knowledge_points", knowledgePoints);
        variables.put("wrongQuestions", summarizeWrongQuestions(wrongQuestions));
        variables.put("wrong_questions", variables.get("wrongQuestions"));
        variables.put("materials", materialMap);
        variables.put("fullPrompt", prompt);
        variables.put("full_prompt", prompt);
        variables.put("jsonFormat", "只输出 JSON，不要 Markdown");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildPlanPrompt(
            int planDays,
            List<String> knowledgePoints,
            List<MockInterviewWrongQuestion> wrongQuestions,
            Map<String, List<MockInterviewLearningPlanVO.Material>> materialMap
    ) {
        return """
                你是求职面试训练教练。请基于用户错题本、薄弱知识点和学习材料，生成 %s 天 AI 面试训练计划。
                要求:
                1. 只输出 JSON，不要 Markdown。
                2. items 数组必须正好 %s 条。
                3. 每天聚焦一个知识点，给出学习目标、练习任务、复习建议。
                4. 计划要具体可执行，不要空泛鼓励。
                JSON 格式:
                {"items":[{"dayNo":1,"title":"第1天：MySQL索引","knowledgePoint":"MySQL索引","learningGoal":"...","practiceTask":"...","reviewSuggestion":"..."}]}
                
                薄弱知识点:
                %s
                
                错题摘要:
                %s
                
                RAG 学习材料:
                %s
                """.formatted(
                planDays,
                planDays,
                String.join("\n", knowledgePoints),
                toJson(summarizeWrongQuestions(wrongQuestions)),
                toJson(materialMap)
        );
    }

    private List<Map<String, Object>> summarizeWrongQuestions(List<MockInterviewWrongQuestion> wrongQuestions) {
        return wrongQuestions.stream().limit(12).map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question", item.getQuestionContent());
            row.put("missingPoints", splitLines(item.getMissingPoints()));
            row.put("suggestions", splitLines(item.getSuggestions()));
            row.put("wrongCount", item.getWrongCount());
            return row;
        }).toList();
    }

    private void refreshPlanStatus(Long planId, Long userId) {
        List<MockInterviewStudyPlanItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<MockInterviewStudyPlanItem>()
                        .eq(MockInterviewStudyPlanItem::getPlanId, planId)
                        .eq(MockInterviewStudyPlanItem::getUserId, userId)
                        .eq(MockInterviewStudyPlanItem::getIsDeleted, NOT_DELETED)
        );
        boolean allDone = !items.isEmpty() && items.stream().allMatch(item -> ITEM_DONE.equals(item.getCompletionStatus()));
        MockInterviewStudyPlan plan = planMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus(allDone ? STATUS_FINISHED : STATUS_ACTIVE);
            planMapper.updateById(plan);
        }
    }

    private MockInterviewLearningPlanVO.Material toMaterial(RagSearchResultVO result) {
        MockInterviewLearningPlanVO.Material material = new MockInterviewLearningPlanVO.Material();
        material.setDocumentId(result.getDocumentId());
        material.setChunkId(result.getChunkId());
        material.setTitle(StringUtils.hasText(result.getTitle()) ? result.getTitle() : result.getReferenceTitle());
        material.setContent(truncate(result.getContent(), 320));
        material.setSource(result.getSource());
        material.setScore(result.getScore());
        return material;
    }

    private int normalizePlanDays(Integer planDays) {
        int value = planDays == null ? 7 : planDays;
        return Math.max(3, Math.min(value, 14));
    }

    private String normalizeItemStatus(String status) {
        String value = status == null ? ITEM_PENDING : status.trim().toUpperCase();
        if (!List.of(ITEM_PENDING, ITEM_DONE).contains(value)) {
            throw new BizException("不支持的完成状态：" + value);
        }
        return value;
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("模型未返回内容");
        }
        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型未返回合法 JSON");
        }
        return cleaned.substring(start, end + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return text.toLowerCase().contains(keyword.toLowerCase());
    }

    private record PlanItemSeed(
            Integer dayNo,
            String title,
            String knowledgePoint,
            String learningGoal,
            String practiceTask,
            String reviewSuggestion
    ) {
    }

    private record LlmPlanResult(List<LlmPlanItem> items) {
    }

    private record LlmPlanItem(
            Integer dayNo,
            String title,
            String knowledgePoint,
            String learningGoal,
            String practiceTask,
            String reviewSuggestion
    ) {
    }

    private record LlmRetestResult(
            Double score,
            Boolean passed,
            String feedback,
            String suggestion
    ) {
    }

    private record RetestEvaluation(
            java.math.BigDecimal score,
            boolean passed,
            String feedback,
            String suggestion
    ) {
    }
}
