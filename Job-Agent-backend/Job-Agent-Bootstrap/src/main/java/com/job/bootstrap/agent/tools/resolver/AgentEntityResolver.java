package com.job.bootstrap.agent.tools.resolver;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.JobCompanyMapper;
import com.job.bootstrap.mapper.JobPositionMapper;
import com.job.bootstrap.mapper.JobResumeMapper;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: Agent 工具实体解析器
 * 日期: 2026/6/25
 */
@Component
@RequiredArgsConstructor
public class AgentEntityResolver {

    private static final int NOT_DELETED = 0;
    private static final int DEFAULT_RESUME = 1;
    private static final int POSITION_PUBLISHED = 1;
    private static final int MAX_JOB_CANDIDATES = 8;

    private final JobResumeMapper jobResumeMapper;
    private final JobPositionMapper jobPositionMapper;
    private final JobCompanyMapper jobCompanyMapper;

    /**
     * 解析简历。
     *
     * 方法步骤:
     * 1. 如果已经有 resumeId，就按当前用户归属校验后返回，兼容旧入口。
     * 2. 如果有 resumeName，就按当前用户和简历名称精确匹配，因为简历名称在用户维度唯一。
     * 3. 如果两者都没有，就尝试使用默认简历，让用户可以说“我的默认简历”。
     * 4. 仍然解析不到时抛出工具参数异常，让上层提示用户补充简历名称。
     */
    public JobResume resolveResumeRequired(Long userId, Long resumeId, String resumeName, String toolName) {
        if (resumeId != null) {
            JobResume resume = jobResumeMapper.selectOne(new LambdaQueryWrapper<JobResume>()
                    .eq(JobResume::getId, resumeId)
                    .eq(JobResume::getUserId, userId)
                    .eq(JobResume::getIsDeleted, NOT_DELETED)
                    .last("limit 1"));
            if (resume != null) {
                return resume;
            }
            throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "没有找到属于你的 resumeId=" + resumeId + " 的简历");
        }

        if (StringUtils.hasText(resumeName)) {
            JobResume resume = jobResumeMapper.selectOne(new LambdaQueryWrapper<JobResume>()
                    .eq(JobResume::getUserId, userId)
                    .eq(JobResume::getResumeName, resumeName.trim())
                    .eq(JobResume::getIsDeleted, NOT_DELETED)
                    .last("limit 1"));
            if (resume != null) {
                return resume;
            }
            throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "没有找到名称为「" + resumeName.trim() + "」的简历");
        }

        JobResume defaultResume = jobResumeMapper.selectOne(new LambdaQueryWrapper<JobResume>()
                .eq(JobResume::getUserId, userId)
                .eq(JobResume::getIsDefault, DEFAULT_RESUME)
                .eq(JobResume::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        if (defaultResume != null) {
            return defaultResume;
        }

        throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "请告诉我要使用哪份简历，例如「黄锋杰(后端)简历」");
    }

    /**
     * 解析岗位。
     *
     * 方法步骤:
     * 1. 如果已有 jobId，就直接按 ID 校验岗位存在，兼容旧入口和用户确认后的入口。
     * 2. 如果只有 jobTitle，就按岗位名称模糊匹配已发布岗位。
     * 3. 命中 0 条时返回参数异常，提示用户换一个岗位名称。
     * 4. 命中 1 条时直接返回岗位。
     * 5. 命中多条时不擅自选择，返回 needClarification=true 和候选列表，让用户确认具体岗位。
     */
    public AgentEntityResolveResult resolveJob(Long jobId, String jobTitle, String toolName) {
        if (jobId != null) {
            JobPosition job = jobPositionMapper.selectOne(new LambdaQueryWrapper<JobPosition>()
                    .eq(JobPosition::getId, jobId)
                    .eq(JobPosition::getIsDeleted, NOT_DELETED)
                    .last("limit 1"));
            if (job != null) {
                return AgentEntityResolveResult.builder().job(job).build();
            }
            throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "没有找到 jobId=" + jobId + " 的岗位");
        }

        if (!StringUtils.hasText(jobTitle)) {
            throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "请告诉我要匹配哪个岗位名称，例如「Java 后端开发」");
        }

        List<JobPosition> jobs = jobPositionMapper.selectList(new LambdaQueryWrapper<JobPosition>()
                .eq(JobPosition::getIsDeleted, NOT_DELETED)
                .eq(JobPosition::getStatus, POSITION_PUBLISHED)
                .like(JobPosition::getJobTitle, jobTitle.trim())
                .orderByDesc(JobPosition::getPublishTime)
                .orderByDesc(JobPosition::getCreateTime)
                .last("limit " + MAX_JOB_CANDIDATES));

        if (jobs.isEmpty()) {
            throw new AgentToolException(AgentToolErrorCode.TOOL_PARAM_MISSING, toolName, "没有找到名称包含「" + jobTitle.trim() + "」的已发布岗位");
        }
        if (jobs.size() == 1) {
            return AgentEntityResolveResult.builder().job(jobs.get(0)).build();
        }

        return AgentEntityResolveResult.builder()
                .needClarification(true)
                .message("匹配到多个「" + jobTitle.trim() + "」相关岗位，请确认你要使用哪一个岗位。")
                .candidates(jobs.stream().map(this::toCandidate).toList())
                .build();
    }

    private Map<String, Object> toCandidate(JobPosition job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobId", job.getId());
        map.put("jobTitle", job.getJobTitle());
        map.put("companyName", resolveCompanyName(job.getCompanyId()));
        map.put("city", job.getCity());
        map.put("district", job.getDistrict());
        map.put("salaryText", buildSalaryText(job));
        map.put("experienceReq", job.getExperienceReq());
        map.put("educationReq", job.getEducationReq());
        return map;
    }

    private String resolveCompanyName(Long companyId) {
        if (companyId == null) {
            return null;
        }
        JobCompany company = jobCompanyMapper.selectOne(new LambdaQueryWrapper<JobCompany>()
                .select(JobCompany::getCompanyName)
                .eq(JobCompany::getId, companyId)
                .eq(JobCompany::getIsDeleted, NOT_DELETED)
                .last("limit 1"));
        return company == null ? null : company.getCompanyName();
    }

    private String buildSalaryText(JobPosition job) {
        Integer minSalary = job.getMinSalary();
        Integer maxSalary = job.getMaxSalary();
        if (minSalary == null && maxSalary == null) {
            return null;
        }
        if (minSalary != null && maxSalary != null) {
            return minSalary + "-" + maxSalary;
        }
        return minSalary != null ? minSalary + "起" : maxSalary + "以内";
    }
}
