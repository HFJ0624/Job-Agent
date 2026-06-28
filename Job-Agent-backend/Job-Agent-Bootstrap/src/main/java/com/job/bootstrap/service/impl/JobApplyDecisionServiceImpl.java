package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplyDecisionRecordMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.JobApplyDecisionService;
import com.job.bootstrap.service.JobMatchService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.decision.JobApplyDecisionRecord;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.decision.JobApplyDecisionVO;
import com.job.common.vo.match.JobMatchVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 功能: AI 投递决策服务实现。
 *
 * 设计:
 * 1. 复用已有岗位匹配结果，避免重复计算“简历和岗位像不像”。
 * 2. 再交给模型判断“是否值得投”，输出决策、风险和行动建议。
 * 3. 模型 JSON 解析失败直接报错，不保存脏记录。
 */
@Service
@RequiredArgsConstructor
public class JobApplyDecisionServiceImpl implements JobApplyDecisionService {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_LLM = "LLM";
    private static final String SCENE_JOB_APPLY_DECISION_GENERATE = "JOB_APPLY_DECISION_GENERATE";

    private final JobApplyDecisionRecordMapper decisionMapper;
    private final JobResumeService resumeService;
    private final JobPositionService positionService;
    private final JobMatchService jobMatchService;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobApplyDecisionVO generateDecision(Long userId, Long resumeId, Long jobId) {
        JobResume resume = resumeService.getUserResumeRequired(userId, resumeId);
        JobPosition job = positionService.getPositionRequired(jobId);
        if (job.getStatus() == null || job.getStatus() != 1) {
            throw new BizException("岗位未发布，不能生成投递决策");
        }

        JobMatchVO match = jobMatchService.getLatestMatch(userId, resumeId, jobId);
        if (match == null) {
            match = jobMatchService.matchJob(userId, resumeId, jobId);
        }

        String traceId = buildTraceId(userId, resumeId, jobId);
        String prompt = buildDecisionPrompt(resume, job, match);
        String response = aiModelGatewayService.chat(
                SCENE_JOB_APPLY_DECISION_GENERATE,
                buildVariables(resume, job, match),
                prompt,
                userId,
                traceId
        );

        LlmDecisionResult result = parseDecisionResult(response);
        JobApplyDecisionRecord record = toRecord(userId, resumeId, job, match, traceId, response, result);
        decisionMapper.insert(record);
        return JobApplyDecisionVO.from(record, objectMapper);
    }

    @Override
    public JobApplyDecisionVO getLatestDecision(Long userId, Long resumeId, Long jobId) {
        JobApplyDecisionRecord record = decisionMapper.selectOne(new LambdaQueryWrapper<JobApplyDecisionRecord>()
                .eq(JobApplyDecisionRecord::getUserId, userId)
                .eq(JobApplyDecisionRecord::getResumeId, resumeId)
                .eq(JobApplyDecisionRecord::getJobId, jobId)
                .eq(JobApplyDecisionRecord::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobApplyDecisionRecord::getCreateTime)
                .last("limit 1"));
        return JobApplyDecisionVO.from(record, objectMapper);
    }

    private Map<String, Object> buildVariables(JobResume resume, JobPosition job, JobMatchVO match) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resumeName", resume.getResumeName());
        variables.put("resume_name", resume.getResumeName());
        variables.put("resumeText", truncate(resume.getRawText(), 1600));
        variables.put("resume_text", variables.get("resumeText"));
        variables.put("jobTitle", job.getJobTitle());
        variables.put("job_title", job.getJobTitle());
        variables.put("jobText", buildJobText(job));
        variables.put("job_text", variables.get("jobText"));
        variables.put("matchResult", match);
        variables.put("match_result", match);
        variables.put("jsonFormat", "只输出 JSON 对象，不要 Markdown，不要解释文本。");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildDecisionPrompt(JobResume resume, JobPosition job, JobMatchVO match) {
        return """
                请你作为求职决策顾问，基于简历、岗位和岗位匹配结果，判断这个岗位是否值得投递。
                
                决策要求:
                1. decision 只能是 APPLY、CAUTIOUS、SKIP。
                2. decisionLabel 只能是 建议投递、谨慎投递、暂不投递。
                3. decisionScore 范围 0-100。
                4. risks、resumeSuggestions、interviewSuggestions、nextActions 每个数组 1-5 条。
                5. 不要只复述匹配分，要给出真实求职行动建议。
                6. 只输出 JSON 对象，不要 Markdown。
                
                JSON 格式:
                {
                  "decision": "APPLY",
                  "decisionLabel": "建议投递",
                  "decisionScore": 86,
                  "reason": "核心理由",
                  "risks": ["风险点"],
                  "resumeSuggestions": ["简历优化建议"],
                  "interviewSuggestions": ["面试准备建议"],
                  "nextActions": ["下一步行动"]
                }
                
                简历名称:
                %s
                
                简历内容:
                %s
                
                岗位:
                %s
                
                岗位信息:
                %s
                
                岗位匹配结果:
                匹配分: %s
                匹配等级: %s
                建议投递: %s
                已匹配技能: %s
                缺失技能: %s
                风险点: %s
                优化建议: %s
                """.formatted(
                safe(resume.getResumeName()),
                truncate(resume.getRawText(), 1800),
                safe(job.getJobTitle()),
                buildJobText(job),
                match.getMatchScore(),
                safe(match.getMatchLevel()),
                Boolean.TRUE.equals(match.getRecommendApply()) ? "是" : "否",
                match.getMatchedSkills(),
                match.getMissingSkills(),
                match.getRiskPoints(),
                match.getSuggestions()
        );
    }

    private JobApplyDecisionRecord toRecord(
            Long userId,
            Long resumeId,
            JobPosition job,
            JobMatchVO match,
            String traceId,
            String response,
            LlmDecisionResult result
    ) {
        JobApplyDecisionRecord record = new JobApplyDecisionRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setJobId(job.getId());
        record.setJobTitle(job.getJobTitle());
        record.setCompanyName(null);
        record.setDecision(normalizeDecision(result.decision()));
        record.setDecisionLabel(normalizeDecisionLabel(result.decisionLabel(), record.getDecision()));
        record.setDecisionScore(toScore(result.decisionScore()));
        record.setReason(result.reason().trim());
        record.setRisksJson(toJson(limitList(result.risks())));
        record.setResumeSuggestionsJson(toJson(limitList(result.resumeSuggestions())));
        record.setInterviewSuggestionsJson(toJson(limitList(result.interviewSuggestions())));
        record.setNextActionsJson(toJson(limitList(result.nextActions())));
        record.setMatchRecordId(match.getId());
        record.setModelTraceId(traceId);
        record.setRawResponse(response);
        record.setSource(SOURCE_LLM);
        record.setIsDeleted(NOT_DELETED);
        return record;
    }

    private LlmDecisionResult parseDecisionResult(String response) {
        try {
            LlmDecisionResult result = objectMapper.readValue(extractJson(response), LlmDecisionResult.class);
            validateResult(result);
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("AI投递决策解析失败，请稍后重试");
        }
    }

    private void validateResult(LlmDecisionResult result) {
        if (result == null
                || !StringUtils.hasText(result.decision())
                || result.decisionScore() == null
                || !StringUtils.hasText(result.reason())) {
            throw new BizException("AI投递决策解析失败，请稍后重试");
        }
    }

    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new BizException("AI投递决策解析失败，请稍后重试");
        }
        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException("AI投递决策解析失败，请稍后重试");
        }
        return cleaned.substring(start, end + 1);
    }

    private String normalizeDecision(String decision) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if ("APPLY".equals(value) || "CAUTIOUS".equals(value) || "SKIP".equals(value)) {
            return value;
        }
        return "CAUTIOUS";
    }

    private String normalizeDecisionLabel(String label, String decision) {
        if (StringUtils.hasText(label)) {
            return label.trim();
        }
        return switch (decision) {
            case "APPLY" -> "建议投递";
            case "SKIP" -> "暂不投递";
            default -> "谨慎投递";
        };
    }

    private BigDecimal toScore(Double score) {
        double value = score == null ? 0 : Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> limitList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(5)
                .toList();
    }

    private String buildJobText(JobPosition job) {
        return String.join("\n",
                "岗位名称: " + safe(job.getJobTitle()),
                "城市: " + safe(job.getCity()),
                "技能关键词: " + safe(job.getSkillKeywords()),
                "岗位描述: " + safe(job.getJobDescription()),
                "任职要求: " + safe(job.getJobRequirement())
        );
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
        } catch (Exception exception) {
            return "[]";
        }
    }

    private String buildTraceId(Long userId, Long resumeId, Long jobId) {
        return "apply_decision_" + userId + "_" + resumeId + "_" + jobId + "_" + UUID.randomUUID();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record LlmDecisionResult(
            String decision,
            String decisionLabel,
            Double decisionScore,
            String reason,
            List<String> risks,
            List<String> resumeSuggestions,
            List<String> interviewSuggestions,
            List<String> nextActions
    ) {
    }
}
