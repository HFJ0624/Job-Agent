package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.InterviewPrepareRecordMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.interview.InterviewPrepareRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.interview.InterviewPrepareVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 作者:hfj
 * 功能:AI 面试准备服务实现
 *
 * 设计说明:
 * 1. 第一版采用规则生成，确保稳定可用。
 * 2. 技术题来自岗位 skillKeywords 和 JD。
 * 3. 项目追问题来自简历 rawText 和岗位要求。
 * 4. 后续接入 LLM 时，可以把这些规则输出作为 Prompt 上下文。
 */
@Service
@RequiredArgsConstructor
public class InterviewPrepareServiceImpl implements InterviewPrepareService {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_RULE = "RULE";

    private final InterviewPrepareRecordMapper interviewPrepareRecordMapper;
    private final JobApplicationRecordMapper jobApplicationRecordMapper;
    private final JobPositionService jobPositionService;
    private final JobResumeService jobResumeService;
    private final ObjectMapper objectMapper;

    /**
     * 生成面试准备。
     */
    @Override
    public InterviewPrepareVO generatePrepare(Long userId, Long applicationId, Long resumeId) {
        /*
         * 1. 校验求职记录归属，防止用户通过改 applicationId 查看别人的数据。
         */
        JobApplicationRecord application = jobApplicationRecordMapper.selectById(applicationId);

        if (application == null || !userId.equals(application.getUserId())) {
            throw new BizException("求职记录不存在或无权限访问");
        }

        /*
         * 2. 查询岗位信息。
         */
        JobPosition job = jobPositionService.getById(application.getJobId());

        if (job == null) {
            throw new BizException("岗位不存在，无法生成面试准备");
        }

        /*
         * 3. 简历ID优先使用请求参数；如果请求没传，就使用求职记录绑定的简历。
         */
        Long finalResumeId = resumeId != null ? resumeId : application.getResumeId();

        JobResume resume = null;
        if (finalResumeId != null) {
            resume = jobResumeService.getUserResumeRequired(userId, finalResumeId);

            /*
             * 如果简历未解析，自动解析一次，方便后续生成项目追问题。
             */
            if (!StringUtils.hasText(resume.getRawText())) {
                resume = jobResumeService.parseResumeText(userId, finalResumeId);
            }
        }

        /*
         * 4. 构建问题列表。
         */
        List<String> technicalQuestions = buildTechnicalQuestions(job);
        List<String> projectQuestions = buildProjectQuestions(job, resume);
        List<String> hrQuestions = buildHrQuestions(application, job);
        List<String> reviewSuggestions = buildReviewSuggestions(job, resume);

        /*
         * 5. 保存记录。
         */
        InterviewPrepareRecord record = new InterviewPrepareRecord();
        record.setUserId(userId);
        record.setApplicationId(applicationId);
        record.setJobId(job.getId());
        record.setResumeId(finalResumeId);
        record.setJobTitle(job.getJobTitle());
        record.setCompanyName(application.getCompanyName());

        record.setTechnicalQuestions(toJson(technicalQuestions));
        record.setProjectQuestions(toJson(projectQuestions));
        record.setHrQuestions(toJson(hrQuestions));
        record.setReviewSuggestions(toJson(reviewSuggestions));

        record.setSummary(buildSummary(application, job, resume));
        record.setSource(SOURCE_RULE);
        record.setIsDeleted(NOT_DELETED);

        interviewPrepareRecordMapper.insert(record);

        return InterviewPrepareVO.from(record, objectMapper);
    }

    /**
     * 查询最近一次面试准备。
     */
    @Override
    public InterviewPrepareVO getLatestPrepare(Long userId, Long applicationId) {
        /*
         * 先校验求职记录归属。
         */
        JobApplicationRecord application = jobApplicationRecordMapper.selectById(applicationId);

        if (application == null || !userId.equals(application.getUserId())) {
            throw new BizException("求职记录不存在或无权限访问");
        }

        InterviewPrepareRecord record = interviewPrepareRecordMapper.selectOne(
                new LambdaQueryWrapper<InterviewPrepareRecord>()
                        .eq(InterviewPrepareRecord::getUserId, userId)
                        .eq(InterviewPrepareRecord::getApplicationId, applicationId)
                        .orderByDesc(InterviewPrepareRecord::getCreateTime)
                        .last("limit 1")
        );

        return InterviewPrepareVO.from(record, objectMapper);
    }

    /**
     * 生成技术面试题。
     */
    private List<String> buildTechnicalQuestions(JobPosition job) {
        List<String> skills = splitKeywords(job.getSkillKeywords());

        List<String> questions = new ArrayList<>();

        /*
         * 根据岗位技能关键词生成技术题。
         */
        for (String skill : skills) {
            questions.add("请介绍你在项目中如何使用 " + skill + "，以及它解决了什么实际问题？");
        }

        /*
         * 根据常见后端岗位补充通用问题。
         */
        String jobText = safe(job.getJobDescription()) + safe(job.getJobRequirement()) + safe(job.getSkillKeywords());

        if (containsAny(jobText, List.of("Redis", "缓存"))) {
            questions.add("Redis 缓存穿透、缓存击穿、缓存雪崩分别是什么？你会如何解决？");
        }

        if (containsAny(jobText, List.of("MySQL", "数据库", "SQL"))) {
            questions.add("MySQL 索引失效有哪些常见场景？如何分析慢 SQL？");
        }

        if (containsAny(jobText, List.of("Spring Boot", "Spring"))) {
            questions.add("Spring Boot 自动配置的原理是什么？你在项目中如何使用 Spring Boot 简化开发？");
        }

        if (containsAny(jobText, List.of("高并发", "并发", "多线程"))) {
            questions.add("如果接口在高并发场景下响应变慢，你会从哪些方面排查？");
        }

        /*
         * 保底题目。
         */
        if (questions.isEmpty()) {
            questions.add("请介绍一个你最熟悉的后端项目，并说明其中的技术架构。");
            questions.add("你在项目中遇到过哪些技术难点？最后是怎么解决的？");
            questions.add("如果让你优化一个接口性能，你会从哪些方向入手？");
        }

        return questions.stream().distinct().limit(10).toList();
    }

    /**
     * 生成项目追问题。
     */
    private List<String> buildProjectQuestions(JobPosition job, JobResume resume) {
        List<String> questions = new ArrayList<>();

        /*
         * 如果有简历文本，就围绕简历项目进行追问。
         */
        if (resume != null && StringUtils.hasText(resume.getRawText())) {
            questions.add("请挑选简历中最有代表性的一个项目，说明项目背景、技术架构和你的个人职责。");
            questions.add("你在项目中负责的核心模块是什么？为什么这样设计？");
            questions.add("项目中是否使用了缓存、消息队列、权限控制或日志监控？具体怎么落地的？");
            questions.add("项目中有没有性能优化或问题排查经历？请说明优化前后的效果。");
        } else {
            questions.add("请准备一个最能体现你后端开发能力的项目案例。");
            questions.add("请提前梳理项目中的技术选型、模块职责和接口设计。");
        }

        /*
         * 根据岗位要求追加项目相关追问。
         */
        if (containsAny(safe(job.getJobRequirement()), List.of("分布式", "微服务"))) {
            questions.add("你的项目是否涉及微服务或分布式设计？服务之间如何通信？");
        }

        if (containsAny(safe(job.getJobRequirement()), List.of("权限", "认证", "登录"))) {
            questions.add("项目中的登录认证和权限控制是怎么设计的？");
        }

        return questions.stream().distinct().limit(8).toList();
    }

    /**
     * 生成 HR 面试题。
     */
    private List<String> buildHrQuestions(JobApplicationRecord application, JobPosition job) {
        List<String> questions = new ArrayList<>();

        questions.add("请做一个 1 分钟左右的自我介绍，重点突出与该岗位相关的项目经历。");
        questions.add("你为什么对 " + safeValue(application.getCompanyName(), "这家公司") + " 的 " + safeValue(job.getJobTitle(), "该岗位") + " 感兴趣？");
        questions.add("你对自己的职业规划是什么？为什么选择这个方向？");
        questions.add("你期望的团队和工作方式是什么？");
        questions.add("如果入职后发现业务复杂度比预期高，你会如何适应？");

        return questions;
    }

    /**
     * 生成复习建议。
     */
    private List<String> buildReviewSuggestions(JobPosition job, JobResume resume) {
        List<String> suggestions = new ArrayList<>();

        List<String> skills = splitKeywords(job.getSkillKeywords());

        if (!skills.isEmpty()) {
            suggestions.add("优先复习岗位技能关键词：" + String.join("、", skills));
        }

        if (containsAny(safe(job.getSkillKeywords()), List.of("Java"))) {
            suggestions.add("复习 Java 集合、JVM、异常处理、多线程和常见设计模式。");
        }

        if (containsAny(safe(job.getSkillKeywords()), List.of("MySQL"))) {
            suggestions.add("复习 MySQL 索引、事务、锁机制、慢 SQL 分析和表结构设计。");
        }

        if (containsAny(safe(job.getSkillKeywords()), List.of("Redis"))) {
            suggestions.add("复习 Redis 数据结构、缓存一致性、过期策略和缓存穿透/击穿/雪崩。");
        }

        if (resume != null && StringUtils.hasText(resume.getRawText())) {
            suggestions.add("把简历中的每个项目整理成“背景-方案-职责-难点-结果”的讲述模板。");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("重点准备项目介绍、岗位技能关键词和常见 HR 问题。");
        }

        return suggestions;
    }

    /**
     * 生成总结。
     */
    private String buildSummary(JobApplicationRecord application, JobPosition job, JobResume resume) {
        return "本次面试准备围绕「"
                + safeValue(application.getCompanyName(), "目标公司")
                + " - "
                + safeValue(job.getJobTitle(), "目标岗位")
                + "」生成，建议优先准备岗位技能关键词、简历项目讲解和 HR 常见问题。";
    }

    /**
     * 拆分技能关键词。
     */
    private List<String> splitKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split("[,，、/\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lower = safe(text).toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeValue(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
