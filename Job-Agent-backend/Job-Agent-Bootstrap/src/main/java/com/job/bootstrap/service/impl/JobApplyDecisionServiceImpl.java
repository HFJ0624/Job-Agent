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
 * AI 投递决策服务实现。
 *
 * <p>核心职责：基于已有岗位匹配结果和大模型分析，判断某岗位对当前简历是否值得投递，
 * 输出决策结论（建议投递 / 谨慎投递 / 暂不投递）、风险点、简历优化建议、面试准备建议和下一步行动。</p>
 *
 * <p>所属业务模块：求职决策 - AI 智能投递决策子模块</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>用户触发：Controller -> {@link #generateDecision}</li>
 *   <li>匹配复用：{@code generateDecision -> JobMatchService.getLatestMatch / matchJob}</li>
 *   <li>模型决策：{@code AiModelGatewayService.chat} 生成结构化决策 JSON</li>
 *   <li>结果持久化：保存 {@link JobApplyDecisionRecord} 并返回 {@link JobApplyDecisionVO}</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>{@link JobMatchService}：复用岗位匹配结果，避免重复计算“简历和岗位像不像”</li>
 *   <li>{@link AiModelGatewayService}：统一模型网关，负责 Prompt 路由和调用日志</li>
 *   <li>{@link JobResumeService} / {@link JobPositionService}：读取简历和岗位基础信息</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>复用已有岗位匹配结果，避免重复计算“简历和岗位像不像”。</li>
 *   <li>再交给模型判断“是否值得投”，输出决策、风险和行动建议。</li>
 *   <li>模型 JSON 解析失败直接报错，不保存脏记录。</li>
 * </ol>
 * </p>
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

    /**
     * 生成简历对指定岗位的 AI 投递决策。
     *
     * <p>核心处理流程：
     * <ol>
     *   <li>校验简历归属和岗位发布状态。</li>
     *   <li>复用或触发岗位匹配，获取匹配结果。</li>
     *   <li>构造决策 Prompt 并调用模型网关生成决策 JSON。</li>
     *   <li>解析并校验模型结果，持久化后返回 VO。</li>
     * </ol>
     * </p>
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param jobId    岗位 ID
     * @return AI 投递决策结果，包含决策结论、风险点和行动建议
     * @throws BizException 岗位未发布、模型解析失败
     */
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

    /**
     * 查询指定简历与岗位最近一次投递决策记录。
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param jobId    岗位 ID
     * @return 最近一次的投递决策结果，不存在时返回 null
     */
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

    /**
     * 构造投递决策 Prompt 变量映射，供后台模板引擎引用。
     *
     * <p>同时提供驼峰和下划线两种 key 风格，兼容不同模板引用习惯。</p>
     *
     * @param resume 简历信息
     * @param job    岗位信息
     * @param match  岗位匹配结果
     * @return 供模型网关使用的变量映射
     */
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

    /**
     * 构造投递决策 Prompt，要求模型输出固定格式的 JSON 决策结果。
     *
     * @param resume 简历信息
     * @param job    岗位信息
     * @param match  岗位匹配结果
     * @return 完整的模型决策 Prompt
     */
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

    /**
     * 将模型决策结果转换为持久化实体。
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param job      岗位信息
     * @param match    岗位匹配结果
     * @param traceId  模型调用 TraceId
     * @param response 模型原始响应
     * @param result   解析后的决策结果
     * @return 待持久化的决策记录
     */
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

    /**
     * 解析模型返回的决策 JSON 并校验必填字段。
     *
     * @param response 模型原始响应文本
     * @return 解析后的决策结果
     * @throws BizException JSON 解析失败或必填字段缺失
     */
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

    /**
     * 校验模型决策结果的关键字段完整性。
     *
     * @param result 解析后的决策结果
     * @throws BizException 关键字段缺失时抛出
     */
    private void validateResult(LlmDecisionResult result) {
        if (result == null
                || !StringUtils.hasText(result.decision())
                || result.decisionScore() == null
                || !StringUtils.hasText(result.reason())) {
            throw new BizException("AI投递决策解析失败，请稍后重试");
        }
    }

    /**
     * 从模型响应中提取 JSON 对象，兼容 Markdown 代码块包裹。
     *
     * @param response 模型原始响应文本
     * @return 纯 JSON 文本
     * @throws BizException 未返回合法 JSON 时抛出
     */
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

    /**
     * 规范化决策编码，非法值时默认返回 CAUTIOUS。
     *
     * @param decision 原始决策编码
     * @return 规范化的标准决策编码
     */
    private String normalizeDecision(String decision) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if ("APPLY".equals(value) || "CAUTIOUS".equals(value) || "SKIP".equals(value)) {
            return value;
        }
        return "CAUTIOUS";
    }

    /**
     * 规范化决策标签，空值时按决策编码映射默认中文标签。
     *
     * @param label    原始决策标签
     * @param decision 规范化的决策编码
     * @return 中文决策标签
     */
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

    /**
     * double 决策分转 BigDecimal，限制在 0~100 范围并保留两位小数。
     *
     * @param score 原始决策分
     * @return 规范化后的 BigDecimal 分数
     */
    private BigDecimal toScore(Double score) {
        double value = score == null ? 0 : Math.max(0, Math.min(100, score));
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 限制字符串列表长度和空值，最多保留 5 条非空文本。
     *
     * @param values 原始字符串列表
     * @return 清洗后的列表
     */
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

    /**
     * 拼接岗位文本，用于 Prompt 中的岗位信息展示。
     *
     * @param job 岗位信息
     * @return 格式化的岗位文本
     */
    private String buildJobText(JobPosition job) {
        return String.join("\n",
                "岗位名称: " + safe(job.getJobTitle()),
                "城市: " + safe(job.getCity()),
                "技能关键词: " + safe(job.getSkillKeywords()),
                "岗位描述: " + safe(job.getJobDescription()),
                "任职要求: " + safe(job.getJobRequirement())
        );
    }

    /**
     * 截断文本至指定最大长度。
     *
     * @param value     原始文本
     * @param maxLength 最大字符数
     * @return 截断后的文本
     */
    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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
        } catch (Exception exception) {
            return "[]";
        }
    }

    /**
     * 构建投递决策模型调用 TraceId。
     *
     * @param userId   当前登录用户 ID
     * @param resumeId 简历 ID
     * @param jobId    岗位 ID
     * @return 唯一 TraceId
     */
    private String buildTraceId(Long userId, Long resumeId, Long jobId) {
        return "apply_decision_" + userId + "_" + resumeId + "_" + jobId + "_" + UUID.randomUUID();
    }

    /**
     * 空值安全的字符串转换，null 时返回空字符串。
     *
     * @param value 原始字符串
     * @return 非空字符串
     */
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
