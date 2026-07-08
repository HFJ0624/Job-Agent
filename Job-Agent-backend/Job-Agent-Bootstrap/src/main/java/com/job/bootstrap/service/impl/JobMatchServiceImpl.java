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
 * 岗位匹配服务实现。
 *
 * <p>核心职责：基于规则评分计算简历与岗位的匹配度，输出匹配分、匹配等级、已匹配/缺失技能、优势、风险点和建议。
 * 第一版不依赖大模型，保证稳定可控、可解释、可测试。</p>
 *
 * <p>所属业务模块：求职匹配 - 简历岗位规则匹配核心服务</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>用户触发 / Agent 调用：Controller -> {@link #matchJob}</li>
 *   <li>数据准备：读取简历原文、岗位信息、提取技能关键词</li>
 *   <li>四维评分：技能匹配(40%) + 项目经验(25%) + 基础条件(20%) + 求职偏好(15%)</li>
 *   <li>持久化：保存 {@link JobMatchRecord} 并返回 {@link JobMatchVO}</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link JobResumeService}：读取简历信息，必要时自动解析简历原文</li>
 *   <li>{@link JobPositionService}：读取岗位详情</li>
 *   <li>{@link JobApplyDecisionServiceImpl}：投递决策服务复用匹配结果，避免重复计算</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>第一版采用规则评分，不依赖大模型，保证稳定可控。</li>
 *   <li>评分维度包括技能匹配、项目经验匹配、基础条件匹配、求职偏好匹配。</li>
 *   <li>后续可以把本服务封装成 JobMatchTool，供 Agent 自动调用。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class JobMatchServiceImpl extends ServiceImpl<JobMatchRecordMapper, JobMatchRecord> implements JobMatchService {

    private static final int NOT_DELETED = 0;

    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final ObjectMapper objectMapper;

    /**
     * 执行简历与岗位的匹配评分。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验简历归属，无解析文本时自动触发解析。</li>
     *   <li>读取并校验岗位信息。</li>
     *   <li>提取岗位技能关键词，计算四维匹配分。</li>
     *   <li>保存匹配记录并返回 VO。</li>
     * </ol>
     * </p>
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param jobId    岗位 ID
     * @return 岗位匹配结果，包含匹配分、等级、技能命中情况等
     * @throws BizException 简历无解析文本、岗位不存在或已删除
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
     * 查询指定简历与岗位最近一次匹配记录。
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param jobId    岗位 ID
     * @return 最近一次的匹配结果，不存在时返回 null
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
     * <p>评分公式：
     * 最终匹配分 = 技能匹配分 * 40%
     *          + 项目经验匹配分 * 25%
     *          + 基础条件匹配分 * 20%
     *          + 求职偏好匹配分 * 15%</p>
     *
     * @param resumeText     简历原文
     * @param jobText        岗位文本
     * @param job            岗位信息
     * @param requiredSkills 岗位要求的技能关键词
     * @return 完整的匹配评分中间结果
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
     * 计算技能匹配分，满分 100。
     *
     * <p>通过简历原文与岗位技能关键词的文本匹配计算命中率，
     * 返回匹配分、已匹配技能和缺失技能。</p>
     *
     * @param resumeText     简历原文
     * @param requiredSkills 岗位要求的技能关键词
     * @return 技能匹配中间结果
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
     * 计算项目经验匹配分，满分 100。
     *
     * <p>不仅看简历有没有技能，还通过关键词判断技能是否出现在项目、系统、模块、优化等上下文中，
     * 以及是否有量化指标（QPS、并发、性能提升等）。</p>
     *
     * @param resumeText    简历原文
     * @param jobText       岗位文本
     * @param matchedSkills 已匹配的技能列表
     * @return 项目经验匹配分
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
     * 计算基础条件匹配分，满分 100。
     *
     * <p>第一版先从简历文本里粗略判断学历和经验是否满足岗位要求，
     * 不满足时按规则扣分。后续可改成读取用户资料表做精确匹配。</p>
     *
     * @param resumeText 简历原文
     * @param job        岗位信息
     * @return 基础条件匹配分
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
     * 计算求职偏好匹配分，满分 100。
     *
     * <p>第一版未接入 user_job_preference 时，先按岗位本身信息给中高分。
     * 若简历文本中出现岗位所在城市，或岗位有明确薪资范围，则适当加分。</p>
     *
     * @param resumeText 简历原文
     * @param job        岗位信息
     * @return 求职偏好匹配分
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
     *
     * <p>优先使用岗位表 skill_keywords 字段，再从岗位描述中命中常见技术关键词做补充。</p>
     *
     * @param jobText       岗位文本
     * @param skillKeywords 岗位表技能关键词字段
     * @return 去重后的技能关键词列表
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
     * 生成岗位匹配优势说明。
     *
     * @param skillScore    技能匹配分
     * @param projectScore  项目经验匹配分
     * @param conditionScore 基础条件匹配分
     * @param matchedSkills 已匹配技能列表
     * @return 优势说明列表
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
     * 生成岗位匹配风险点说明。
     *
     * @param missingSkills  缺失技能列表
     * @param conditionScore 基础条件匹配分
     * @param projectScore   项目经验匹配分
     * @return 风险点说明列表
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
     * 生成简历优化和投递建议。
     *
     * @param missingSkills 缺失技能列表
     * @param projectScore  项目经验匹配分
     * @param job           岗位信息
     * @return 优化建议列表
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
     * 根据匹配分数映射匹配等级。
     *
     * @param score 匹配分数
     * @return 匹配等级，如 "高度匹配"、"较匹配"、"一般匹配"、"不匹配"
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
     * 判断简历学历是否可能满足岗位要求。
     *
     * <p>第一版基于简历文本关键词做粗略判断，"不限"时直接通过。</p>
     *
     * @param resumeText  简历原文
     * @param educationReq 岗位学历要求
     * @return 是否可能满足
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
     * 拼接岗位相关文本，用于统一关键词匹配。
     *
     * @param job 岗位信息
     * @return 包含岗位名称、描述、要求和技能关键词的合并文本
     */
    private String buildJobText(JobPosition job) {
        return normalizeText(
                safe(job.getJobTitle()) + "\n" +
                        safe(job.getJobDescription()) + "\n" +
                        safe(job.getJobRequirement()) + "\n" +
                        safe(job.getSkillKeywords())
        );
    }

    /**
     * 判断文本中是否包含任意一个关键词（不区分大小写）。
     *
     * @param text     待检查文本
     * @param keywords 关键词列表
     * @return 包含任一关键词时返回 true
     */
    private boolean containsAny(String text, List<String> keywords) {
        String lowerText = safe(text).toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 规范化文本，统一换行符和制表符。
     *
     * @param text 原始文本
     * @return 规范化后的文本
     */
    private String normalizeText(String text) {
        return safe(text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\t", " ")
                .trim();
    }

    /**
     * 空值安全的字符串转换，null 时返回空字符串。
     *
     * @param text 原始字符串
     * @return 非空字符串
     */
    private String safe(String text) {
        return text == null ? "" : text;
    }

    /**
     * double 转 BigDecimal，保留两位小数。
     *
     * @param value 原始 double 值
     * @return 保留两位小数的 BigDecimal
     */
    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 将字符串列表按换行符拼接为单字符串。
     *
     * @param lines 字符串列表
     * @return 拼接后的文本，空列表时返回空字符串
     */
    private String joinLines(List<String> lines) {
        return lines == null || lines.isEmpty() ? "" : String.join("\n", lines);
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串，失败时返回 "[]"
     */
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
