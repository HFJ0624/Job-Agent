package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.InterviewQuestionBankMapper;
import com.job.bootstrap.mapper.MockInterviewQuestionMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.InterviewQuestionSelectorService;
import com.job.common.entity.interview.InterviewQuestionBank;
import com.job.common.entity.interview.MockInterviewQuestion;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 模拟面试题目选择服务实现。
 *
 * 方法核心:
 * 1. 用岗位和简历构造 RAG 检索 Query，优先召回 INTERVIEW_QUESTION chunk。
 * 2. 从 chunk metadata 中取 questionBankId，回查题库主表，保证拿到完整题目和标准答案。
 * 3. 对 RAG 命中题和主表候选题统一做相似题去重。
 * 4. 按 EASY / MEDIUM / HARD 做难度配比，避免一场面试全是同一难度。
 * 5. RAG 不足时用主表关键词候选补齐。
 */
@Service
@RequiredArgsConstructor
public class InterviewQuestionSelectorServiceImpl implements InterviewQuestionSelectorService {

    private static final long PUBLIC_USER_ID = 0L;
    private static final int NOT_DELETED = 0;
    private static final String ACTIVE = "ACTIVE";
    private static final String DOCUMENT_TYPE_INTERVIEW_QUESTION = "INTERVIEW_QUESTION";
    private static final String EASY = "EASY";
    private static final String MEDIUM = "MEDIUM";
    private static final String HARD = "HARD";

    private final InterviewQuestionBankMapper questionBankMapper;
    private final MockInterviewQuestionMapper mockQuestionMapper;
    private final RagRetrievalService ragRetrievalService;

    @Override
    public List<InterviewQuestionBank> selectQuestions(JobPosition job, JobResume resume, int questionCount) {
        return selectQuestions(null, job, resume, questionCount, DEFAULT_EXCLUDE_RECENT_HOURS);
    }

    @Override
    public List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours
    ) {
        int targetCount = Math.max(1, questionCount);
        List<String> keywords = buildQuestionKeywords(job, resume);
        String query = buildRagQuery(job, resume, keywords);

        List<InterviewQuestionBank> ragQuestions = loadRagQuestions(query, targetCount);
        List<InterviewQuestionBank> tableQuestions = loadTableQuestions(keywords);

        /*
         * 1. RAG 命中的题排在前面，因为它已经经过向量、关键词和重排序综合召回。
         * 2. 主表候选作为补充，解决 embedding 未建好、RAG 召回不足或新题刚导入时的兜底问题。
         * 3. 后续统一去重和难度配比，避免 RAG 前几条相似题把名额占满。
         */
        List<InterviewQuestionBank> candidates = new ArrayList<>();
        candidates.addAll(ragQuestions);
        candidates.addAll(tableQuestions);

        List<InterviewQuestionBank> distinctCandidates = distinctSimilarQuestions(candidates);
        Set<Long> recentQuestionIds = loadRecentQuestionBankIds(userId, excludeRecentHours);
        List<InterviewQuestionBank> freshCandidates = filterRecentQuestions(distinctCandidates, recentQuestionIds);
        List<InterviewQuestionBank> selected = balanceDifficulty(freshCandidates, targetCount);

        /*
         * 最近题去重是“优先避开”，不是“题库不足就失败”。
         * 当过滤后题目不够时，再从最近已抽过的候选里补齐，保证用户仍然能开始面试。
         */
        if (selected.size() < targetCount && !recentQuestionIds.isEmpty()) {
            List<InterviewQuestionBank> backfillCandidates = distinctCandidates.stream()
                    .filter(question -> question.getId() != null && recentQuestionIds.contains(question.getId()))
                    .toList();
            selected = mergeSelectedQuestions(selected, balanceDifficulty(backfillCandidates, targetCount), targetCount);
        }
        return selected;
    }

    private List<InterviewQuestionBank> loadRagQuestions(String query, int questionCount) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        int recallLimit = Math.max(questionCount * 6, 20);
        List<RagSearchResultVO> ragResults;
        try {
            ragResults = ragRetrievalService.search(PUBLIC_USER_ID, query, recallLimit);
        } catch (Exception exception) {
            return List.of();
        }

        List<Long> questionIds = ragResults.stream()
                .filter(result -> DOCUMENT_TYPE_INTERVIEW_QUESTION.equals(result.getDocumentType()))
                .map(this::extractQuestionBankId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(questionIds)) {
            return List.of();
        }

        Map<Long, InterviewQuestionBank> questionMap = questionBankMapper.selectBatchIds(questionIds).stream()
                .filter(this::isActiveQuestion)
                .collect(Collectors.toMap(InterviewQuestionBank::getId, item -> item, (oldValue, newValue) -> oldValue));

        List<InterviewQuestionBank> orderedQuestions = new ArrayList<>();
        for (Long questionId : questionIds) {
            InterviewQuestionBank question = questionMap.get(questionId);
            if (question != null) {
                orderedQuestions.add(question);
            }
        }
        return orderedQuestions;
    }

    private List<InterviewQuestionBank> loadTableQuestions(List<String> keywords) {
        List<InterviewQuestionBank> questions = questionBankMapper.selectList(new LambdaQueryWrapper<InterviewQuestionBank>()
                .eq(InterviewQuestionBank::getStatus, ACTIVE)
                .eq(InterviewQuestionBank::getIsDeleted, NOT_DELETED));
        if (CollectionUtils.isEmpty(questions)) {
            return List.of();
        }

        return questions.stream()
                .map(question -> new ScoredQuestion(question, scoreQuestion(question, keywords)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator
                        .comparingInt(ScoredQuestion::score)
                        .reversed()
                        .thenComparing(item -> item.question().getId(), Comparator.nullsLast(Long::compareTo)))
                .map(ScoredQuestion::question)
                .toList();
    }

    private List<InterviewQuestionBank> distinctSimilarQuestions(List<InterviewQuestionBank> candidates) {
        Set<Long> seenIds = new LinkedHashSet<>();
        Set<String> seenFingerprints = new LinkedHashSet<>();
        List<InterviewQuestionBank> result = new ArrayList<>();

        for (InterviewQuestionBank question : candidates) {
            if (!isActiveQuestion(question)) {
                continue;
            }
            Long id = question.getId();
            if (id != null && !seenIds.add(id)) {
                continue;
            }

            String fingerprint = normalizeQuestionFingerprint(question.getQuestionTitle());
            if (StringUtils.hasText(fingerprint) && !seenFingerprints.add(fingerprint)) {
                continue;
            }
            result.add(question);
        }
        return result;
    }

    private Set<Long> loadRecentQuestionBankIds(Long userId, Integer excludeRecentHours) {
        int windowHours = normalizeExcludeRecentHours(excludeRecentHours);
        if (userId == null || windowHours <= 0) {
            return Set.of();
        }

        Date startTime = new Date(System.currentTimeMillis() - windowHours * 60L * 60L * 1000L);
        List<MockInterviewQuestion> recentQuestions = mockQuestionMapper.selectList(new LambdaQueryWrapper<MockInterviewQuestion>()
                .eq(MockInterviewQuestion::getUserId, userId)
                .eq(MockInterviewQuestion::getIsDeleted, NOT_DELETED)
                .isNotNull(MockInterviewQuestion::getQuestionBankId)
                .ge(MockInterviewQuestion::getCreateTime, startTime));
        if (CollectionUtils.isEmpty(recentQuestions)) {
            return Set.of();
        }

        return recentQuestions.stream()
                .map(MockInterviewQuestion::getQuestionBankId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int normalizeExcludeRecentHours(Integer excludeRecentHours) {
        if (excludeRecentHours == null) {
            return DEFAULT_EXCLUDE_RECENT_HOURS;
        }
        return Math.max(0, Math.min(excludeRecentHours, 24 * 30));
    }

    private List<InterviewQuestionBank> filterRecentQuestions(
            List<InterviewQuestionBank> candidates,
            Set<Long> recentQuestionIds
    ) {
        if (recentQuestionIds.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(question -> question.getId() == null || !recentQuestionIds.contains(question.getId()))
                .toList();
    }

    private List<InterviewQuestionBank> mergeSelectedQuestions(
            List<InterviewQuestionBank> primary,
            List<InterviewQuestionBank> backfill,
            int questionCount
    ) {
        List<InterviewQuestionBank> result = new ArrayList<>(primary);
        Set<Long> selectedIds = primary.stream()
                .map(InterviewQuestionBank::getId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (InterviewQuestionBank question : backfill) {
            addQuestion(question, result, selectedIds, questionCount);
            if (result.size() >= questionCount) {
                break;
            }
        }
        return result;
    }

    private List<InterviewQuestionBank> balanceDifficulty(List<InterviewQuestionBank> candidates, int questionCount) {
        Map<String, List<InterviewQuestionBank>> byDifficulty = new LinkedHashMap<>();
        byDifficulty.put(EASY, new ArrayList<>());
        byDifficulty.put(MEDIUM, new ArrayList<>());
        byDifficulty.put(HARD, new ArrayList<>());

        for (InterviewQuestionBank question : candidates) {
            byDifficulty.computeIfAbsent(normalizeDifficulty(question.getDifficulty()), key -> new ArrayList<>())
                    .add(question);
        }

        List<String> plan = buildDifficultyPlan(questionCount);
        List<InterviewQuestionBank> selected = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        for (String difficulty : plan) {
            addNextQuestion(byDifficulty.get(difficulty), selected, selectedIds, questionCount);
        }
        for (InterviewQuestionBank question : candidates) {
            addQuestion(question, selected, selectedIds, questionCount);
        }
        return selected;
    }

    private List<String> buildDifficultyPlan(int questionCount) {
        /*
         * 题目少时优先保证覆盖中等难度；题目多时按 20% EASY、60% MEDIUM、20% HARD 近似配比。
         */
        int easyCount = Math.max(1, Math.round(questionCount * 0.2F));
        int hardCount = questionCount >= 3 ? Math.max(1, Math.round(questionCount * 0.2F)) : 0;
        int mediumCount = Math.max(0, questionCount - easyCount - hardCount);

        List<String> plan = new ArrayList<>();
        addDifficulty(plan, EASY, easyCount);
        addDifficulty(plan, MEDIUM, mediumCount);
        addDifficulty(plan, HARD, hardCount);
        return plan;
    }

    private void addDifficulty(List<String> plan, String difficulty, int count) {
        for (int index = 0; index < count; index++) {
            plan.add(difficulty);
        }
    }

    private void addNextQuestion(
            List<InterviewQuestionBank> questions,
            List<InterviewQuestionBank> selected,
            Set<Long> selectedIds,
            int questionCount
    ) {
        if (CollectionUtils.isEmpty(questions) || selected.size() >= questionCount) {
            return;
        }
        for (InterviewQuestionBank question : questions) {
            if (addQuestion(question, selected, selectedIds, questionCount)) {
                return;
            }
        }
    }

    private boolean addQuestion(
            InterviewQuestionBank question,
            List<InterviewQuestionBank> selected,
            Set<Long> selectedIds,
            int questionCount
    ) {
        if (question == null || selected.size() >= questionCount) {
            return false;
        }
        Long id = question.getId();
        if (id != null && !selectedIds.add(id)) {
            return false;
        }
        selected.add(question);
        return true;
    }

    private List<String> buildQuestionKeywords(JobPosition job, JobResume resume) {
        Set<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, job.getJobTitle());
        addKeyword(keywords, job.getJobCategory());
        addKeyword(keywords, job.getSkillKeywords());
        addKeyword(keywords, job.getJobDescription());
        addKeyword(keywords, job.getJobRequirement());
        addKeyword(keywords, resume.getRawText());
        return keywords.stream().limit(24).toList();
    }

    private String buildRagQuery(JobPosition job, JobResume resume, List<String> keywords) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "岗位名称", job.getJobTitle());
        appendLine(builder, "岗位分类", job.getJobCategory());
        appendLine(builder, "技能关键词", job.getSkillKeywords());
        appendLine(builder, "岗位描述", truncate(job.getJobDescription(), 600));
        appendLine(builder, "任职要求", truncate(job.getJobRequirement(), 600));
        appendLine(builder, "简历摘要", truncate(resume.getRawText(), 600));
        appendLine(builder, "检索关键词", String.join(" ", keywords));
        return builder.toString();
    }

    private void addKeyword(Set<String> keywords, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (String item : text.split("[,，、\\s/|;；:：()（）\\[\\]【】]+")) {
            String keyword = item.trim();
            if (keyword.length() >= 2 && keyword.length() <= 30) {
                keywords.add(keyword);
            }
            if (keywords.size() >= 24) {
                return;
            }
        }
    }

    private int scoreQuestion(InterviewQuestionBank question, List<String> keywords) {
        String title = safe(question.getQuestionTitle());
        String category = safe(question.getCategory());
        String tags = safe(question.getTags());
        String answer = safe(question.getStandardAnswer());

        int score = 0;
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (containsIgnoreCase(title, keyword)) {
                score += 8;
            }
            if (containsIgnoreCase(category, keyword)) {
                score += 6;
            }
            if (containsIgnoreCase(tags, keyword)) {
                score += 6;
            }
            if (containsIgnoreCase(answer, keyword)) {
                score += 2;
            }
        }
        return score;
    }

    private Long extractQuestionBankId(RagSearchResultVO result) {
        if (result.getBusinessId() != null && DOCUMENT_TYPE_INTERVIEW_QUESTION.equals(result.getDocumentType())) {
            return result.getBusinessId();
        }
        Map<String, Object> metadata = result.getMetadata();
        if (metadata == null || !metadata.containsKey("questionBankId")) {
            return null;
        }
        Object value = metadata.get("questionBankId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    private boolean isActiveQuestion(InterviewQuestionBank question) {
        return question != null
                && ACTIVE.equals(question.getStatus())
                && Integer.valueOf(NOT_DELETED).equals(question.getIsDeleted())
                && StringUtils.hasText(question.getQuestionTitle())
                && StringUtils.hasText(question.getStandardAnswer());
    }

    private String normalizeDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return MEDIUM;
        }
        String value = difficulty.trim().toUpperCase(Locale.ROOT);
        if (EASY.equals(value) || MEDIUM.equals(value) || HARD.equals(value)) {
            return value;
        }
        return MEDIUM;
    }

    private String normalizeQuestionFingerprint(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：！？、（）【】《》]+", "")
                .trim();
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredQuestion(InterviewQuestionBank question, int score) {
    }
}
