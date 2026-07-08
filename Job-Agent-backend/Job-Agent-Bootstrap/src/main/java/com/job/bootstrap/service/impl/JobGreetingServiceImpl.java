package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobGreetingRecordMapper;
import com.job.bootstrap.service.*;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.greeting.JobGreetingRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.greeting.GreetingVO;
import com.job.common.vo.match.JobMatchVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
/**
 * HR 打招呼语生成服务实现。
 *
 * <p>核心职责：根据用户简历、岗位信息和匹配技能，生成适合发送给 HR 的打招呼语，并自动创建沟通记录。</p>
 *
 * <p>所属业务模块：求职沟通模块（communication）/ 打招呼语模块（greeting）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>前端调用 {@link #generateGreeting} 传入简历、岗位和语气风格；</li>
 *   <li>服务优先读取岗位匹配结果中的 matchedSkills，无匹配记录则从简历文本与岗位 skillKeywords 中简单命中；</li>
 *   <li>按风格模板生成话术正文，保存到 job_greeting_record；</li>
 *   <li>自动生成沟通记录，保证用户复制话术后业务链路不断裂。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link JobResumeService} 获取并解析简历文本；</li>
 *   <li>依赖 {@link JobPositionService} 获取岗位信息；</li>
 *   <li>依赖 {@link JobMatchService} 获取最近一次岗位匹配结果；</li>
 *   <li>依赖 {@link JobCompanyService} 查询公司名称；</li>
 *   <li>调用 {@link JobCommunicationRecordService} 自动创建沟通记录。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>第一版先使用规则模板生成，不依赖大模型，保证功能稳定可用；</li>
 *   <li>优先复用岗位匹配结果中的 matchedSkills，提升话术与岗位的相关性；</li>
 *   <li>如果没有匹配记录，则从岗位技能关键词和简历原文中做一次简单命中；</li>
 *   <li>支持多种语气风格（正式、自然、自信、实习生风格、社招风格、简洁直达）；</li>
 *   <li>后续接入 LLM 时，只需替换 {@link #buildGreetingContent} 方法即可，上游逻辑无需改动。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class JobGreetingServiceImpl
        extends ServiceImpl<JobGreetingRecordMapper, JobGreetingRecord>
        implements JobGreetingService {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_RULE = "RULE";

    private final JobResumeService jobResumeService;
    private final JobPositionService jobPositionService;
    private final JobMatchService jobMatchService;
    private final ObjectMapper objectMapper;
    private final JobCompanyService jobCompanyService;
    private final JobCommunicationRecordService jobCommunicationRecordService;

    /**
     * 生成 HR 打招呼语。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GreetingVO generateGreeting(Long userId, Long resumeId, Long jobId, String style) {
        // 1. 校验简历归属，避免用户使用别人的简历生成话术。
        JobResume resume = jobResumeService.getUserResumeRequired(userId, resumeId);

        // 2. 如果简历没有 rawText，先自动解析一次。
        if (!StringUtils.hasText(resume.getRawText())) {
            resume = jobResumeService.parseResumeText(userId, resumeId);
        }

        if (!StringUtils.hasText(resume.getRawText())) {
            throw new BizException("当前简历没有解析文本，请先解析简历后再生成打招呼语");
        }

        // 3. 查询岗位信息。
        JobPosition job = jobPositionService.getById(jobId);
        if (job == null) {
            throw new BizException("岗位不存在或已被删除");
        }

        // 4. 规范化语气风格，前端不传时默认“自然”。
        String finalStyle = normalizeStyle(style);

        // 5. 优先读取最近一次岗位匹配结果，从里面拿已匹配技能。
        JobMatchVO latestMatch = jobMatchService.getLatestMatch(userId, resumeId, jobId);

        List<String> matchedSkills = latestMatch == null
                ? extractMatchedSkillsFromText(resume.getRawText(), job.getSkillKeywords())
                : latestMatch.getMatchedSkills();

        // 6. 最多展示3个技能，避免话术太长。
        List<String> topSkills = matchedSkills.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());

        // 7. 生成话术正文。
        String content = buildGreetingContent(job, topSkills, finalStyle);

        // 8. 保存生成记录。
        JobGreetingRecord record = new JobGreetingRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setJobId(jobId);
        record.setStyle(finalStyle);
        record.setContent(content);
        record.setMatchedSkills(toJson(topSkills));
        record.setSource(SOURCE_RULE);
        record.setIsDeleted(NOT_DELETED);
        save(record);

        /*
         * 生成打招呼语后，自动创建沟通记录。
         *
         * 业务意义:
         * 1. 用户生成话术后，下一步大概率会复制到 Boss 直聘和 HR 沟通。
         * 2. 所以这里自动创建一条沟通记录，避免生成完话术后业务链路断掉。
         * 3. 前端“沟通记录”页面可以直接看到这条记录。
         */
        jobCommunicationRecordService.createFromGreeting(
                userId,
                resumeId,
                jobId,
                record.getId(),
                content
        );

        return GreetingVO.from(record, objectMapper);
    }

    /**
     * 生成打招呼语正文。
     *
     * 说明:
     * 1. 第一版使用模板生成，稳定可控。
     * 2. 后续可以替换为 LLM Prompt：
     *    公司名称 + 岗位名称 + 岗位要求 + 简历优势 + 匹配技能 + 风格。
     */
    private String buildGreetingContent(JobPosition job, List<String> topSkills, String style) {
        String companyName = safeCompanyName(job);
        String jobTitle = safe(job.getJobTitle(), "该岗位");
        String skillText = topSkills.isEmpty()
                ? "后端开发、项目开发和业务落地"
                : String.join("、", topSkills);

        return switch (style) {
            case "正式" -> String.format(
                    "您好，我对贵公司%s比较感兴趣。我目前具备%s相关经验，看到岗位要求与我的项目经历较为匹配，希望有机会进一步沟通，谢谢。",
                    jobTitle,
                    skillText
            );

            case "自信" -> String.format(
                    "您好，我关注到贵公司的%s岗位。我在%s方面有一定实践经验，认为自己的技术栈和项目经历与岗位要求比较匹配，期待有机会和您进一步沟通。",
                    jobTitle,
                    skillText
            );

            case "实习生风格" -> String.format(
                    "您好，我对贵公司的%s岗位很感兴趣。我目前学习并实践过%s相关内容，也希望在真实业务中继续提升自己，期待能获得一次沟通机会，谢谢。",
                    jobTitle,
                    skillText
            );

            case "社招风格" -> String.format(
                    "您好，我看到贵公司正在招聘%s。我过往项目中涉及%s等相关内容，比较关注岗位所需的业务落地和工程实践能力，希望有机会进一步沟通。",
                    jobTitle,
                    skillText
            );

            case "简洁直达" -> String.format(
                    "您好，我对%s岗位感兴趣，具备%s相关经验，想进一步了解岗位情况，期待沟通。",
                    jobTitle,
                    skillText
            );

            default -> String.format(
                    "您好，我对%s的%s岗位比较感兴趣。我目前具备%s相关经验，看到岗位方向与我的经历比较匹配，希望有机会进一步沟通，谢谢。",
                    companyName,
                    jobTitle,
                    skillText
            );
        };
    }

    /**
     * 如果没有岗位匹配记录，就从简历文本和岗位 skillKeywords 中做简单技能命中。
     */
    private List<String> extractMatchedSkillsFromText(String resumeText, String skillKeywords) {
        if (!StringUtils.hasText(skillKeywords)) {
            return Collections.emptyList();
        }

        String lowerResumeText = resumeText.toLowerCase(Locale.ROOT);

        return Arrays.stream(skillKeywords.split("[,，、/\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(skill -> lowerResumeText.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 规范化语气风格。
     */
    private String normalizeStyle(String style) {
        if (!StringUtils.hasText(style)) {
            return "自然";
        }

        Set<String> allowedStyles = Set.of(
                "正式",
                "自然",
                "自信",
                "实习生风格",
                "社招风格",
                "简洁直达"
        );

        String trimmed = style.trim();
        return allowedStyles.contains(trimmed) ? trimmed : "自然";
    }

    /**
     * 兼容岗位表中没有公司名的情况。
     * 如果你的 JobPosition 实体没有 getCompanyName()，这里改成通过 companyId 查询公司名称。
     */
    private String safeCompanyName(JobPosition job) {
        try {
            //通过职位公司id查询公司名称
            JobCompany company = jobCompanyService.getCompanyRequired(job.getCompanyId());
            return StringUtils.hasText(company.getCompanyName()) ? company.getCompanyName() : "贵公司";
        } catch (Exception e) {
            return "贵公司";
        }
    }

    /**
     * 字符串兜底。
     */
    private String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 对象转 JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
