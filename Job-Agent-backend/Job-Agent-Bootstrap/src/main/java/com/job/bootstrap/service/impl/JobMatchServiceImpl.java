package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobMatchRecordMapper;
import com.job.bootstrap.service.JobMatchService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.match.JobMatchRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
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
 * 功能:岗位匹配服务实现类
 * 设计思路:
 * 1. 第一版采用规则评分，不依赖大模型，保证稳定可控。
 * 2. 评分维度包括技能匹配、项目经验匹配、基础条件匹配、求职偏好匹配。
 * 3. 后续可以把本服务封装成 JobMatchTool，供 Agent 自动调用。
 * 日期: 2026/6/8 11:00
 */
@Service
@RequiredArgsConstructor
public class JobMatchServiceImpl extends ServiceImpl<JobMatchRecordMapper, JobMatchRecord> implements JobMatchService {

    private static final int NOT_DELETED = 0;

    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final ObjectMapper objectMapper;

    /**
     * 执行岗位匹配。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobMatchVO matchJob(Long userId, Long resumeId, Long jobId) {
        // 1. 校验简历归属，防止用户传别人的 resumeId。
        JobResume resume = jobResumeService.getUserResumeRequired(userId, resumeId);

        // 2. 如果简历还没有 rawText，先自动解析一次。
        if (!StringUtils.hasText(resume.getRawText())) {
            resume = jobResumeService.parseResumeText(userId, resumeId);
        }

        if (!StringUtils.hasText(resume.getRawText())) {
            throw new BizException("当前简历没有解析文本，请先解析简历后再进行岗位匹配");
        }

        // 3. 查询岗位信息。
        JobPosition job = jobPositionService.getById(jobId);
        if (job == null) {
            throw new BizException("岗位不存在或已被删除");
        }

        // 4. 组合岗位文本，方便后面统一做关键词匹配。
        String jobText = buildJobText(job);
        String resumeText = normalizeText(resume.getRawText());

        // 5. 从岗位中提取技能关键词。
        List<String> requiredSkills = extractRequiredSkills(jobText, job.getSkillKeywords());

        // 6. 计算四个维度分数。
        MatchScoreDetail detail = calculateMatchScore(resumeText, jobText, job, requiredSkills);

        // 7. 保存匹配记录。
        Date now = new Date();
        JobMatchRecord record = new JobMatchRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setJobId(jobId);

        record.setMatchScore(detail.matchScore());
        record.setRuleScore(detail.matchScore());
        record.setSkillScore(detail.skillScore());
        record.setProjectScore(detail.projectScore());
        record.setConditionScore(detail.conditionScore());
        record.setPreferenceScore(detail.preferenceScore());

        record.setMatchLevel(resolveMatchLevel(detail.matchScore()));
        record.setRecommendApply(detail.matchScore().doubleValue() >= 70 ? 1 : 0);

        record.setMatchedSkills(toJson(detail.matchedSkills()));
        record.setMissingSkills(toJson(detail.missingSkills()));
        record.setAdvantage(joinLines(detail.advantages()));
        record.setRiskPoints(joinLines(detail.riskPoints()));
        record.setSuggestion(joinLines(detail.suggestions()));
        record.setScoreJson(toJson(detail));

        record.setIsDeleted(NOT_DELETED);
        record.setCreateTime(now);
        record.setUpdateTime(now);

        save(record);
        return JobMatchVO.from(record, objectMapper);
    }

    /**
     * 查询最近一次岗位匹配记录。
     */
    @Override
    public JobMatchVO getLatestMatch(Long userId, Long resumeId, Long jobId) {
        JobMatchRecord record = getOne(
                new LambdaQueryWrapper<JobMatchRecord>()
                        .eq(JobMatchRecord::getUserId, userId)
                        .eq(JobMatchRecord::getResumeId, resumeId)
                        .eq(JobMatchRecord::getJobId, jobId)
                        .eq(JobMatchRecord::getIsDeleted, NOT_DELETED)
                        .orderByDesc(JobMatchRecord::getCreateTime)
                        .last("limit 1"),
                false
        );

        return JobMatchVO.from(record, objectMapper);
    }

    /**
     * 计算岗位匹配分。
     *
     * 评分公式:
     * 最终匹配分 = 技能匹配分 * 40%
     *          + 项目经验匹配分 * 25%
     *          + 基础条件匹配分 * 20%
     *          + 求职偏好匹配分 * 15%
     */
    private MatchScoreDetail calculateMatchScore(
            String resumeText,
            String jobText,
            JobPosition job,
            List<String> requiredSkills
    ) {
        SkillMatchResult skillResult = calculateSkillScore(resumeText, requiredSkills);

        BigDecimal skillScore = skillResult.skillScore();
        BigDecimal projectScore = calculateProjectScore(resumeText, jobText, skillResult.matchedSkills());
        BigDecimal conditionScore = calculateConditionScore(resumeText, job);
        BigDecimal preferenceScore = calculatePreferenceScore(resumeText, job);

        BigDecimal matchScore = skillScore.multiply(decimal(0.40))
                .add(projectScore.multiply(decimal(0.25)))
                .add(conditionScore.multiply(decimal(0.20)))
                .add(preferenceScore.multiply(decimal(0.15)))
                .setScale(2, RoundingMode.HALF_UP);

        List<String> advantages = buildAdvantages(skillScore, projectScore, conditionScore, skillResult.matchedSkills());
        List<String> risks = buildRiskPoints(skillResult.missingSkills(), conditionScore, projectScore);
        List<String> suggestions = buildSuggestions(skillResult.missingSkills(), projectScore, job);

        return new MatchScoreDetail(
                matchScore,
                skillScore,
                projectScore,
                conditionScore,
                preferenceScore,
                skillResult.matchedSkills(),
                skillResult.missingSkills(),
                advantages,
                risks,
                suggestions
        );
    }

    /**
     * 技能匹配分，满分100。
     * 说明：岗位要求的技能命中越多，分数越高。
     */
    private SkillMatchResult calculateSkillScore(String resumeText, List<String> requiredSkills) {
        if (requiredSkills.isEmpty()) {
            return new SkillMatchResult(decimal(60), Collections.emptyList(), Collections.emptyList());
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String lowerResume = resumeText.toLowerCase(Locale.ROOT);

        for (String skill : requiredSkills) {
            if (lowerResume.contains(skill.toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        double hitRate = matched.size() * 1.0 / requiredSkills.size();
        return new SkillMatchResult(decimal(hitRate * 100), matched, missing);
    }

    /**
     * 项目经验匹配分，满分100。
     * 说明：不仅看简历有没有技能，还要看技能是否出现在项目、系统、模块、优化等上下文中。
     */
    private BigDecimal calculateProjectScore(String resumeText, String jobText, List<String> matchedSkills) {
        double score = 40;

        if (containsAny(resumeText, List.of("项目", "系统", "平台", "模块", "负责", "实现", "优化"))) {
            score += 20;
        }

        if (containsAny(resumeText, List.of("接口", "数据库", "缓存", "消息队列", "权限", "部署", "日志"))) {
            score += 15;
        }

        if (containsAny(resumeText, List.of("QPS", "并发", "性能", "响应时间", "提升", "%", "ms"))) {
            score += 15;
        }

        if (!matchedSkills.isEmpty()) {
            score += Math.min(matchedSkills.size() * 2.0, 10);
        }

        return decimal(Math.min(score, 100));
    }

    /**
     * 基础条件匹配分，满分100。
     * 说明：第一版先从简历文本里粗略判断学历和经验，后续可以改成读取用户资料表。
     */
    private BigDecimal calculateConditionScore(String resumeText, JobPosition job) {
        double score = 100;

        String educationReq = safe(job.getEducationReq());
        String experienceReq = safe(job.getExperienceReq());

        // 学历要求不满足时扣分。
        if (StringUtils.hasText(educationReq) && !maySatisfyEducation(resumeText, educationReq)) {
            score -= 20;
        }

        // 经验要求较高但简历中没有实习/工作/项目经历时扣分。
        if (StringUtils.hasText(experienceReq)
                && containsAny(experienceReq, List.of("3年", "5年", "经验"))
                && !containsAny(resumeText, List.of("实习", "工作经历", "项目", "公司"))) {
            score -= 20;
        }

        return decimal(Math.max(score, 0));
    }

    /**
     * 求职偏好匹配分，满分100。
     * 说明：第一版没有接 user_job_preference 时，先按岗位本身给中高分。
     * 后续可以读取用户期望城市、薪资、行业、公司规模后再精细计算。
     */
    private BigDecimal calculatePreferenceScore(String resumeText, JobPosition job) {
        double score = 80;

        if (StringUtils.hasText(job.getCity()) && resumeText.contains(job.getCity())) {
            score += 10;
        }

        if (job.getMinSalary() != null && job.getMaxSalary() != null) {
            score += 10;
        }

        return decimal(Math.min(score, 100));
    }

    /**
     * 从岗位文本和 skillKeywords 字段提取技能关键词。
     */
    private List<String> extractRequiredSkills(String jobText, String skillKeywords) {
        Set<String> result = new LinkedHashSet<>();

        // 1. 优先使用岗位表里的 skill_keywords 字段。
        if (StringUtils.hasText(skillKeywords)) {
            Arrays.stream(skillKeywords.split("[,，/、\\s]+"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(result::add);
        }

        // 2. 再从岗位描述中命中常见技术关键词。
        List<String> commonSkills = List.of(
                "Java", "Spring Boot", "Spring Cloud", "MyBatis", "MySQL", "Redis",
                "RabbitMQ", "Kafka", "Elasticsearch", "Docker", "Linux", "Nginx",
                "Vue", "React", "TypeScript", "Python", "Go", "微服务", "分布式",
                "高并发", "缓存", "消息队列", "事务", "JVM", "多线程"
        );

        for (String skill : commonSkills) {
            if (jobText.toLowerCase(Locale.ROOT).contains(skill.toLowerCase(Locale.ROOT))) {
                result.add(skill);
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * 生成优势说明。
     */
    private List<String> buildAdvantages(
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal conditionScore,
            List<String> matchedSkills
    ) {
        List<String> advantages = new ArrayList<>();

        if (skillScore.doubleValue() >= 70) {
            advantages.add("简历技能栈与岗位要求匹配度较高，已命中多个核心技能。");
        }

        if (projectScore.doubleValue() >= 70) {
            advantages.add("项目经历能够支撑部分岗位要求，具备一定工程实践基础。");
        }

        if (conditionScore.doubleValue() >= 80) {
            advantages.add("基础条件与岗位要求整体匹配，没有明显硬性短板。");
        }

        if (!matchedSkills.isEmpty()) {
            advantages.add("已匹配技能：" + String.join("、", matchedSkills));
        }

        if (advantages.isEmpty()) {
            advantages.add("简历与岗位存在一定关联，但需要进一步强化项目描述和技能证明。");
        }

        return advantages;
    }

    /**
     * 生成风险点。
     */
    private List<String> buildRiskPoints(
            List<String> missingSkills,
            BigDecimal conditionScore,
            BigDecimal projectScore
    ) {
        List<String> risks = new ArrayList<>();

        if (!missingSkills.isEmpty()) {
            risks.add("岗位要求中部分技能未在简历中体现：" + String.join("、", missingSkills));
        }

        if (conditionScore.doubleValue() < 70) {
            risks.add("学历、经验或城市等基础条件可能与岗位要求存在差距。");
        }

        if (projectScore.doubleValue() < 60) {
            risks.add("项目经历对岗位要求的支撑不足，缺少具体技术难点或量化结果。");
        }

        return risks;
    }

    /**
     * 生成优化建议。
     */
    private List<String> buildSuggestions(List<String> missingSkills, BigDecimal projectScore, JobPosition job) {
        List<String> suggestions = new ArrayList<>();

        if (!missingSkills.isEmpty()) {
            suggestions.add("建议补充或学习岗位缺失技能：" + String.join("、", missingSkills));
        }

        if (projectScore.doubleValue() < 70) {
            suggestions.add("建议在项目经历中补充“业务背景 + 技术方案 + 个人贡献 + 量化效果”。");
        }

        if (StringUtils.hasText(job.getJobTitle())) {
            suggestions.add("投递「" + job.getJobTitle() + "」时，可以优先突出与岗位技能关键词相关的项目内容。");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("当前匹配度较好，可以针对岗位 JD 微调简历关键词后再投递。");
        }

        return suggestions;
    }

    /**
     * 根据分数生成匹配等级。
     */
    private String resolveMatchLevel(BigDecimal score) {
        double value = score.doubleValue();

        if (value >= 85) {
            return "高度匹配";
        }
        if (value >= 70) {
            return "较匹配";
        }
        if (value >= 55) {
            return "一般匹配";
        }
        return "不匹配";
    }

    /**
     * 判断学历是否可能满足。
     */
    private boolean maySatisfyEducation(String resumeText, String educationReq) {
        if (!StringUtils.hasText(educationReq)) {
            return true;
        }

        if (educationReq.contains("不限")) {
            return true;
        }

        if (educationReq.contains("本科")) {
            return containsAny(resumeText, List.of("本科", "硕士", "研究生", "博士"));
        }

        if (educationReq.contains("硕士")) {
            return containsAny(resumeText, List.of("硕士", "研究生", "博士"));
        }

        if (educationReq.contains("专科")) {
            return containsAny(resumeText, List.of("专科", "本科", "硕士", "研究生", "博士"));
        }

        return true;
    }

    /**
     * 拼接岗位文本。
     */
    private String buildJobText(JobPosition job) {
        return normalizeText(
                safe(job.getJobTitle()) + "\n" +
                        safe(job.getJobDescription()) + "\n" +
                        safe(job.getJobRequirement()) + "\n" +
                        safe(job.getSkillKeywords())
        );
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lowerText = safe(text).toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeText(String text) {
        return safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", " ")
                .trim();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String joinLines(List<String> lines) {
        return lines == null || lines.isEmpty() ? "" : String.join("\n", lines);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 技能匹配中间结果。
     */
    private record SkillMatchResult(
            BigDecimal skillScore,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {}

    /**
     * 完整匹配评分中间结果。
     */
    private record MatchScoreDetail(
            BigDecimal matchScore,
            BigDecimal skillScore,
            BigDecimal projectScore,
            BigDecimal conditionScore,
            BigDecimal preferenceScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> advantages,
            List<String> riskPoints,
            List<String> suggestions
    ) {}
}
