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
 * <p>核心职责：根据岗位 JD、简历内容和用户薄弱知识点，通过 RAG 检索 + 题库关键词匹配召回合适题目，
 * 经过去重、避重和难度配比后返回最终题目列表。
 *
 * <p>所属业务模块：面试训练中心 - 模拟面试题目选择（Interview Question Selector）。
 *
 * <p>主要调用链：
 * <ul>
 *   <li>{@code selectQuestions} → 构建关键词 → RAG 召回 → 主表候选 → 去重 → 避重 → 难度配比 → 返回题目。</li>
 * </ul>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link RagRetrievalService}：通过向量检索召回与岗位/简历相关的面试题 chunk。</li>
 *   <li>{@link InterviewQuestionBankMapper}：回查题库主表获取完整题目和标准答案。</li>
 *   <li>{@link MockInterviewQuestionMapper}：查询用户近期已做过的题目，用于避重。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>RAG 优先：向量语义匹配能召回关键词不直接覆盖但语义相关的题目，提升匹配精度。</li>
 *   <li>主表兜底：RAG 召回不足或 embedding 未建好时，通过关键词打分从主表补齐，保证可用性。</li>
 *   <li>相似去重：基于题目内容指纹去重，避免同一知识点变体题占满名额。</li>
 *   <li>难度配比：按 20% EASY、60% MEDIUM、20% HARD 近似配比，避免单场面试难度失衡。</li>
 *   <li>避重策略：优先排除近期已做过的题目，题库不足时允许回退，确保用户始终能开始面试。</li>
 * </ul>
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

    /**
     * 为模拟面试选择题目（简易入口，无用户避重）。
     *
     * @param job           岗位信息
     * @param resume        简历信息
     * @param questionCount 目标题目数量
     * @return 选中的题目列表
     */
    @Override
    public List<InterviewQuestionBank> selectQuestions(JobPosition job, JobResume resume, int questionCount) {
        return selectQuestions(null, job, resume, questionCount, DEFAULT_EXCLUDE_RECENT_HOURS);
    }

    /**
     * 为模拟面试选择题目（支持用户避重）。
     *
     * @param userId             用户 ID
     * @param job                岗位信息
     * @param resume             简历信息
     * @param questionCount      目标题目数量
     * @param excludeRecentHours 排除近期已做题目的时间窗口（小时）
     * @return 选中的题目列表
     */
    @Override
    public List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours
    ) {
        return selectQuestions(userId, job, resume, questionCount, excludeRecentHours, List.of());
    }

    /**
     * 为模拟面试选择题目（完整入口，支持薄弱知识点加权）。
     *
     * <p>步骤：构建关键词 → RAG 召回 → 主表候选 → 相似去重 → 避重过滤 → 难度配比 → 回退补齐。
     *
     * @param userId             用户 ID
     * @param job                岗位信息
     * @param resume             简历信息
     * @param questionCount      目标题目数量
     * @param excludeRecentHours 排除近期已做题目的时间窗口（小时）
     * @param weakKeywords       用户薄弱知识点关键词，用于提升相关题目权重
     * @return 选中的题目列表
     */
    @Override
    public List<InterviewQuestionBank> selectQuestions(
            Long userId,
            JobPosition job,
            JobResume resume,
            int questionCount,
            Integer excludeRecentHours,
            List<String> weakKeywords
    ) {
        int targetCount = Math.max(1, questionCount);
        List<String> keywords = buildQuestionKeywords(job, resume, weakKeywords);
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

    /**
     * 通过 RAG 检索召回面试题。
     *
     * <p>按文档类型过滤出 INTERVIEW_QUESTION chunk，提取 questionBankId 后回查主表，按 RAG 得分顺序返回。
     *
     * @param query         RAG 检索 Query
     * @param questionCount 目标题目数量（用于计算召回倍数）
     * @return RAG 命中并激活的题目列表
     */
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

    /**
     * 通过关键词打分从题库主表加载候选题目。
     *
     * <p>按标题、分类、标签、标准答案与关键词的匹配度打分，降序返回。
     *
     * @param keywords 关键词列表
     * @return 主表候选题目列表
     */
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

    /**
     * 对候选题目进行 ID 和内容指纹去重。
     *
     * <p>基于题目标题的规范化指纹判断是否相似，避免同一知识点的变体题重复出现。
     *
     * @param candidates 候选题目列表
     * @return 去重后的题目列表
     */
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

    /**
     * 加载用户近期已做过的题库题目 ID 集合。
     *
     * @param userId             用户 ID
     * @param excludeRecentHours 时间窗口（小时）
     * @return 近期已做题目 ID 集合
     */
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

    /**
     * 按难度配比对候选题目进行平衡选择。
     *
     * <p>优先按 EASY / MEDIUM / HARD 的计划数量依次选取，不足时从剩余候选中补齐。
     *
     * @param candidates    候选题目列表
     * @param questionCount 目标题目数量
     * @return 难度配比后的题目列表
     */
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

    /**
     * 构建题目选择关键词集合。
     *
     * <p>综合薄弱知识点、岗位标题、分类、技能关键词、JD、任职要求及简历文本生成关键词，最多 24 个。
     *
     * @param job         岗位信息
     * @param resume      简历信息
     * @param weakKeywords 薄弱知识点关键词
     * @return 关键词列表
     */
    private List<String> buildQuestionKeywords(JobPosition job, JobResume resume, List<String> weakKeywords) {
        Set<String> keywords = new LinkedHashSet<>();
        if (weakKeywords != null) {
            weakKeywords.forEach(item -> addKeyword(keywords, item));
        }
        addKeyword(keywords, job.getJobTitle());
        addKeyword(keywords, job.getJobCategory());
        addKeyword(keywords, job.getSkillKeywords());
        addKeyword(keywords, job.getJobDescription());
        addKeyword(keywords, job.getJobRequirement());
        addKeyword(keywords, resume.getRawText());
        return keywords.stream().limit(24).toList();
    }

    /**
     * 构建 RAG 检索 Query。
     *
     * <p>将岗位信息、简历摘要和关键词组装为结构化检索文本，用于向量召回。
     *
     * @param job      岗位信息
     * @param resume   简历信息
     * @param keywords 关键词列表
     * @return RAG 检索 Query
     */
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

    /**
     * 计算题目与关键词的匹配得分。
     *
     * <p>标题匹配权重最高（8 分），分类和标签次之（6 分），标准答案最低（2 分）。
     *
     * @param question 题库题目
     * @param keywords 关键词列表
     * @return 匹配得分
     */
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

    /**
     * 从 RAG 检索结果中提取题库题目 ID。
     *
     * <p>优先取 businessId，其次从 metadata 中解析 questionBankId。
     *
     * @param result RAG 检索结果
     * @return 题库题目 ID，无法提取时返回 {@code null}
     */
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

    /**
     * 判断题库题目是否处于有效状态。
     *
     * <p>有效条件：状态为 ACTIVE、未逻辑删除、标题和标准答案均非空。
     *
     * @param question 题库题目
     * @return true 表示题目有效
     */
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
