package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobResumeScoreRecordMapper;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import com.job.common.entity.resume.JobResumeScoreRecord;
import com.job.common.vo.resume.ResumeScoreVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 作者:hfj
 * 功能:简历评分业务实现
 * 日期:2026/6/6
 *
 * 设计说明：
 * 1. 第一版先使用规则评分，保证系统稳定、可解释、可测试。
 * 2. 后续接入大模型时，可以在本类中增加 LLM 评分结果，或者把本类封装成 ResumeAnalyzeTool。
 * 3. 评分维度采用 100 分制：
 *    基础信息10 + 教育背景10 + 技能栈20 + 项目经历35 + 实习/工作经历15 + 表达质量10。
 */
@Service
@RequiredArgsConstructor
public class JobResumeScoreServiceImpl
        extends ServiceImpl<JobResumeScoreRecordMapper, JobResumeScoreRecord>
        implements JobResumeScoreService {

    /**
     * 简历评分完成后的状态。
     */
    private static final String STATUS_SCORED = "SCORED";

    /**
     * 解析失败状态。
     */
    private static final String STATUS_PARSE_FAILED = "PARSE_FAILED";

    /**
     * 未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 常见技术关键词。
     * 说明：第一版用关键词命中做技能栈评分，后续可以换成技能字典表或 JD 技能抽取。
     */
    private static final List<String> TECH_KEYWORDS = List.of(
            "Java", "Spring", "Spring Boot", "Spring Cloud", "MyBatis", "MyBatis-Plus",
            "MySQL", "Redis", "MongoDB", "Elasticsearch", "RabbitMQ", "Kafka",
            "Docker", "Kubernetes", "Linux", "Nginx", "Git", "Maven",
            "Vue", "React", "TypeScript", "JavaScript", "HTML", "CSS",
            "Python", "Go", "C++", "微服务", "分布式", "高并发", "缓存", "消息队列",
            "事务", "接口", "数据库", "SQL", "JVM", "多线程"
    );

    /**
     * 教育背景关键词。
     */
    private static final List<String> EDUCATION_KEYWORDS = List.of(
            "大学", "学院", "本科", "硕士", "研究生", "专科", "博士", "专业", "计算机",
            "软件工程", "网络工程", "人工智能", "数据科学"
    );

    /**
     * 项目经历关键词。
     */
    private static final List<String> PROJECT_KEYWORDS = List.of(
            "项目", "系统", "平台", "模块", "接口", "业务", "权限", "订单", "用户",
            "后台", "管理", "开发", "设计", "实现", "优化", "负责"
    );

    /**
     * 量化结果关键词。
     */
    private static final List<String> METRIC_KEYWORDS = List.of(
            "%", "ms", "秒", "分钟", "QPS", "TPS", "并发", "提升", "降低", "减少",
            "优化", "性能", "响应时间", "吞吐量", "成功率"
    );

    private final JobResumeService jobResumeService;
    private final ObjectMapper objectMapper;

    /**
     * 对当前用户指定简历进行评分。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeScoreVO scoreResume(Long userId, Long resumeId, String targetPosition) {
        // 1. 先校验简历归属，避免用户通过改ID评分别人的简历。
        JobResume resume = jobResumeService.getUserResumeRequired(userId, resumeId);

        // 2. 如果简历还没有解析文本，就复用已有的 parseResumeText 能力先解析。
        if (!StringUtils.hasText(resume.getRawText())) {
            resume = jobResumeService.parseResumeText(userId, resumeId);
        }

        // 3. 解析失败或没有有效文本时，不继续评分。
        if (!StringUtils.hasText(resume.getRawText()) || STATUS_PARSE_FAILED.equals(resume.getStatus())) {
            throw new BizException("当前简历没有可用的解析文本，请先上传可复制文字的 PDF、DOC 或 DOCX 简历");
        }

        // 4. 执行规则评分，得到各维度分数和问题建议。
        ResumeRuleScore ruleScore = calculateRuleScore(resume.getRawText(), targetPosition);

        // 5. 保存评分记录。
        Date now = new Date();
        JobResumeScoreRecord record = new JobResumeScoreRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setTargetPosition(trimToNull(targetPosition));

        record.setTotalScore(ruleScore.totalScore());
        record.setBasicInfoScore(ruleScore.basicInfoScore());
        record.setEducationScore(ruleScore.educationScore());
        record.setSkillScore(ruleScore.skillScore());
        record.setProjectScore(ruleScore.projectScore());
        record.setExperienceScore(ruleScore.experienceScore());
        record.setExpressionScore(ruleScore.expressionScore());

        record.setAdvantage(joinLines(ruleScore.advantages()));
        record.setProblem(joinLines(ruleScore.problems()));
        record.setSuggestion(joinLines(ruleScore.suggestions()));
        record.setScoreJson(toJson(ruleScore));

        record.setIsDeleted(NOT_DELETED);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        save(record);

        // 6. 同步更新 resume 表上的 score 字段，方便简历列表直接展示总分。
        resume.setScore(ruleScore.totalScore());
        resume.setStatus(STATUS_SCORED);
        resume.setUpdateTime(now);
        jobResumeService.updateById(resume);

        return ResumeScoreVO.from(record);
    }

    /**
     * 查询最近一次评分记录。
     */
    @Override
    public ResumeScoreVO getLatestScore(Long userId, Long resumeId) {
        JobResumeScoreRecord record = getOne(new LambdaQueryWrapper<JobResumeScoreRecord>()
                .eq(JobResumeScoreRecord::getUserId, userId)
                .eq(JobResumeScoreRecord::getResumeId, resumeId)
                .eq(JobResumeScoreRecord::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobResumeScoreRecord::getCreateTime)
                .last("limit 1"), false);

        return ResumeScoreVO.from(record);
    }

    /**
     * 核心规则评分逻辑。
     *
     * @param rawText 简历解析文本
     * @param targetPosition 目标岗位
     * @return 规则评分结果
     */
    private ResumeRuleScore calculateRuleScore(String rawText, String targetPosition) {
        String text = normalizeText(rawText);

        BigDecimal basicInfoScore = scoreBasicInfo(text);
        BigDecimal educationScore = scoreEducation(text);
        BigDecimal skillScore = scoreSkill(text);
        BigDecimal projectScore = scoreProject(text);
        BigDecimal experienceScore = scoreExperience(text);
        BigDecimal expressionScore = scoreExpression(text);

        BigDecimal totalScore = basicInfoScore
                .add(educationScore)
                .add(skillScore)
                .add(projectScore)
                .add(experienceScore)
                .add(expressionScore)
                .setScale(2, RoundingMode.HALF_UP);

        List<String> advantages = buildAdvantages(
                basicInfoScore,
                educationScore,
                skillScore,
                projectScore,
                experienceScore,
                expressionScore,
                targetPosition
        );

        List<String> problems = buildProblems(
                basicInfoScore,
                educationScore,
                skillScore,
                projectScore,
                experienceScore,
                expressionScore
        );

        List<String> suggestions = buildSuggestions(
                skillScore,
                projectScore,
                experienceScore,
                expressionScore,
                targetPosition
        );

        return new ResumeRuleScore(
                totalScore,
                basicInfoScore,
                educationScore,
                skillScore,
                projectScore,
                experienceScore,
                expressionScore,
                advantages,
                problems,
                suggestions
        );
    }

    /**
     * 基础信息评分，满分10。
     */
    private BigDecimal scoreBasicInfo(String text) {
        double score = 0;

        // 手机号命中，加3分。
        if (text.matches("(?s).*1[3-9]\\d{9}.*")) {
            score += 3;
        }

        // 邮箱命中，加3分。
        if (text.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")) {
            score += 3;
        }

        // 求职意向或目标岗位命中，加2分。
        if (containsAny(text, List.of("求职意向", "应聘岗位", "目标岗位", "期望岗位", "Java后端", "后端开发"))) {
            score += 2;
        }

        // GitHub、博客、作品集等加2分。
        if (containsAny(text, List.of("GitHub", "Gitee", "博客", "Blog", "作品", "个人网站", "开源"))) {
            score += 2;
        }

        return decimal(Math.min(score, 10));
    }

    /**
     * 教育背景评分，满分10。
     */
    private BigDecimal scoreEducation(String text) {
        int hitCount = countKeywordHits(text, EDUCATION_KEYWORDS);
        double score = Math.min(hitCount * 2.0, 8.0);

        // 如果出现明确时间段，说明教育经历更完整。
        if (text.matches("(?s).*20\\d{2}.*20\\d{2}.*")) {
            score += 2;
        }

        return decimal(Math.min(score, 10));
    }

    /**
     * 技能栈评分，满分20。
     */
    private BigDecimal scoreSkill(String text) {
        int hitCount = countKeywordHits(text, TECH_KEYWORDS);

        // 每命中一个技术关键词给2分，最多18分。
        double score = Math.min(hitCount * 2.0, 18.0);

        // 如果技能描述有熟练程度，说明不是简单堆关键词。
        if (containsAny(text, List.of("熟悉", "掌握", "了解", "精通", "使用", "具备"))) {
            score += 2;
        }

        return decimal(Math.min(score, 20));
    }

    /**
     * 项目经历评分，满分35。
     */
    private BigDecimal scoreProject(String text) {
        double score = 0;

        // 项目相关关键词越多，项目经历越完整。
        int projectHitCount = countKeywordHits(text, PROJECT_KEYWORDS);
        score += Math.min(projectHitCount * 1.5, 12);

        // 技术关键词出现在项目文本中，说明技能不是只写在技能栏。
        int techHitCount = countKeywordHits(text, TECH_KEYWORDS);
        score += Math.min(techHitCount * 1.2, 10);

        // 出现“负责/设计/实现/优化”等个人贡献表达。
        if (containsAny(text, List.of("负责", "设计", "实现", "开发", "优化", "封装", "重构", "排查"))) {
            score += 5;
        }

        // 出现量化结果，说明项目描述更有说服力。
        int metricHitCount = countKeywordHits(text, METRIC_KEYWORDS);
        score += Math.min(metricHitCount * 2.0, 5);

        // 出现权限、缓存、消息队列、高并发、部署等工程化内容，加分。
        if (containsAny(text, List.of("权限", "缓存", "消息队列", "高并发", "部署", "日志", "监控", "限流", "事务"))) {
            score += 3;
        }

        return decimal(Math.min(score, 35));
    }

    /**
     * 实习/工作经历评分，满分15。
     */
    private BigDecimal scoreExperience(String text) {
        double score = 0;

        if (containsAny(text, List.of("实习", "工作经历", "任职", "公司", "岗位", "职责"))) {
            score += 8;
        }

        if (containsAny(text, List.of("参与", "负责", "独立", "协作", "上线", "交付", "维护"))) {
            score += 4;
        }

        if (text.matches("(?s).*20\\d{2}[./年-].*20\\d{2}.*")) {
            score += 3;
        }

        return decimal(Math.min(score, 15));
    }

    /**
     * 表达质量评分，满分10。
     */
    private BigDecimal scoreExpression(String text) {
        double score = 10;

        // 文本过短，一般说明简历内容不足或解析效果不好。
        if (text.length() < 800) {
            score -= 3;
        }

        // 文本过长，可能存在无关内容或格式混乱。
        if (text.length() > 20000) {
            score -= 2;
        }

        // 出现明显解析失败残留或乱码，扣分。
        if (containsAny(text, List.of("解析失败", "乱码", "����", "image1.png", "word/media"))) {
            score -= 4;
        }

        // 大段没有换行会影响阅读。
        if (!text.contains("\n") && text.length() > 1000) {
            score -= 2;
        }

        return decimal(Math.max(score, 0));
    }

    /**
     * 生成优势列表。
     */
    private List<String> buildAdvantages(
            BigDecimal basicInfoScore,
            BigDecimal educationScore,
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal experienceScore,
            BigDecimal expressionScore,
            String targetPosition
    ) {
        List<String> advantages = new ArrayList<>();

        if (skillScore.doubleValue() >= 14) {
            advantages.add("技能栈覆盖较完整，具备较好的岗位基础匹配能力。");
        }

        if (projectScore.doubleValue() >= 24) {
            advantages.add("项目经历较丰富，能够体现一定的业务开发和工程实践能力。");
        }

        if (experienceScore.doubleValue() >= 10) {
            advantages.add("实习或工作经历描述较完整，具备真实业务场景经验。");
        }

        if (expressionScore.doubleValue() >= 8) {
            advantages.add("简历整体表达较清晰，结构和可读性较好。");
        }

        if (StringUtils.hasText(targetPosition)) {
            advantages.add("本次评分已结合目标岗位「" + targetPosition.trim() + "」进行建议归纳。");
        }

        if (advantages.isEmpty()) {
            advantages.add("简历已经具备基础内容，可以在项目经历和技能证明上继续增强。");
        }

        return advantages;
    }

    /**
     * 生成问题列表。
     */
    private List<String> buildProblems(
            BigDecimal basicInfoScore,
            BigDecimal educationScore,
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal experienceScore,
            BigDecimal expressionScore
    ) {
        List<String> problems = new ArrayList<>();

        if (basicInfoScore.doubleValue() < 7) {
            problems.add("基础信息不够完整，建议补充联系方式、求职意向、GitHub或个人作品链接。");
        }

        if (educationScore.doubleValue() < 6) {
            problems.add("教育背景信息不够完整，建议补充学校、专业、学历、时间和相关课程。");
        }

        if (skillScore.doubleValue() < 12) {
            problems.add("技能栈覆盖不足或表达较弱，建议补充核心技术并体现掌握程度。");
        }

        if (projectScore.doubleValue() < 20) {
            problems.add("项目经历说服力不足，缺少技术难点、个人贡献或量化结果。");
        }

        if (experienceScore.doubleValue() < 8) {
            problems.add("实习或工作经历较弱，如果没有正式经历，应进一步强化项目经历。");
        }

        if (expressionScore.doubleValue() < 7) {
            problems.add("简历表达或格式存在优化空间，建议减少大段描述并突出重点。");
        }

        return problems;
    }

    /**
     * 生成优化建议列表。
     */
    private List<String> buildSuggestions(
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal experienceScore,
            BigDecimal expressionScore,
            String targetPosition
    ) {
        List<String> suggestions = new ArrayList<>();

        if (StringUtils.hasText(targetPosition)) {
            suggestions.add("围绕目标岗位「" + targetPosition.trim() + "」重新组织技能栈，把最相关的技术放在前面。");
        }

        if (skillScore.doubleValue() < 16) {
            suggestions.add("技能栏不要只堆关键词，建议按“后端框架 / 数据库 / 缓存 / 中间件 / 部署工具”分类展示。");
        }

        if (projectScore.doubleValue() < 28) {
            suggestions.add("项目经历建议采用“背景 + 技术方案 + 个人负责内容 + 最终效果”的结构描述。");
            suggestions.add("尽量补充量化指标，例如接口响应时间、并发量、数据量、性能提升比例等。");
        }

        if (experienceScore.doubleValue() < 10) {
            suggestions.add("如果缺少实习经历，可以把课程设计、个人项目或开源项目写得更工程化。");
        }

        if (expressionScore.doubleValue() < 8) {
            suggestions.add("控制每段项目描述长度，避免大段文字堆积，使用短句和项目符号提升可读性。");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("当前简历整体质量较好，后续可以针对不同岗位微调项目关键词和技能顺序。");
        }

        return suggestions;
    }

    /**
     * 统计关键词命中数量。
     */
    private int countKeywordHits(String text, List<String> keywords) {
        int count = 0;
        String lowerText = text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }

        return count;
    }

    /**
     * 判断文本是否包含任意关键词。
     */
    private boolean containsAny(String text, List<String> keywords) {
        String lowerText = text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 清洗文本。
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\t', ' ')
                .trim();
    }

    /**
     * 转 BigDecimal 并保留两位小数。
     */
    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 多行文本拼接。
     */
    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        return String.join("\n", lines);
    }

    /**
     * 字符串清洗。
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 对象转 JSON。
     * 说明：转失败不影响主流程，只保存空JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    /**
     * 规则评分内部结果。
     */
    private record ResumeRuleScore(
            BigDecimal totalScore,
            BigDecimal basicInfoScore,
            BigDecimal educationScore,
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal experienceScore,
            BigDecimal expressionScore,
            List<String> advantages,
            List<String> problems,
            List<String> suggestions
    ) {
    }
}
