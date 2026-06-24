package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobResumeScoreRecordMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.resume.ResumeScoreRuleEngine;
import com.job.common.entity.resume.JobResume;
import com.job.common.entity.resume.JobResumeScoreRecord;
import com.job.common.vo.resume.ResumeScoreVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:简历 AI 评分业务实现
 * 日期:2026/6/15
 *
 * V2 设计说明:
 * 1. 先用 ResumeScoreRuleEngine 计算稳定分数，保证评分可解释、可测试、可重复。
 * 2. 配置了大模型路由时，评分接口会优先等待统一模型网关返回，让页面直接看到 AI 参与后的结果。
 * 3. 大模型作为第二评分员参与维度打分和建议生成，规则引擎只负责提供稳定初始分和兜底。
 * 4. 大模型结果回来后按“规则分 65% + 模型分 35%”合并，既体现模型参与，又避免模型分数大幅漂移。
 * 5. score_json 保存完整 V2 结构，老字段继续写入，兼容当前数据库和旧前端字段。
 */
@Service
@RequiredArgsConstructor
public class JobResumeScoreServiceImpl
        extends ServiceImpl<JobResumeScoreRecordMapper, JobResumeScoreRecord>
        implements JobResumeScoreService {

    /**
     * 简历评分模型场景。
     * 说明: 该场景由 ai_model_route 绑定模型，由 ai_prompt_template / ai_prompt_version 管理 Prompt。
     */
    private static final String AI_SCENE_RESUME_SCORE = "RESUME_SCORE";

    /**
     * 简历评分完成后的状态。
     */
    private static final String STATUS_SCORED = "SCORED";

    /**
     * 简历解析失败状态。
     */
    private static final String STATUS_PARSE_FAILED = "PARSE_FAILED";

    /**
     * 未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 送给模型的简历最大字符数。
     * 说明:
     * 1. 原来把 12000 字符简历 + 完整规则 JSON 一次塞给模型，容易导致请求超过 20 秒。
     * 2. 这里保留足够模型判断质量的主体内容，同时减少上下文长度，让模型更快返回。
     */
    private static final int LLM_RESUME_TEXT_LIMIT = 3500;

    /**
     * 大模型分数在最终分里的权重。
     * 说明:
     * 1. 规则分负责稳定、可复现，适合做基础分。
     * 2. 模型分负责理解语义、判断项目质量和表达问题，适合做修正分。
     * 3. 35% 是偏保守的权重，能体现模型参与，但不会因为一次模型输出异常把总分拉得过高或过低。
     */
    private static final double LLM_SCORE_WEIGHT = 0.35;

    private final JobResumeService jobResumeService;
    private final ResumeScoreRuleEngine resumeScoreRuleEngine;
    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 对当前用户指定简历执行 V2 AI 评分。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @param targetPosition 用户填写的求职方向，可为空，不作为 JD 匹配评分
     * @return 前端展示用评分结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeScoreVO scoreResume(Long userId, Long resumeId, String targetPosition) {
        // 1. 校验简历归属，防止用户通过改 ID 评分或读取别人的简历。
        JobResume resume = jobResumeService.getUserResumeRequired(userId, resumeId);

        // 2. 如果简历还没有解析文本，复用已有解析能力先得到 rawText。
        if (!StringUtils.hasText(resume.getRawText())) {
            resume = jobResumeService.parseResumeText(userId, resumeId);
        }

        // 3. 没有有效文本就不能评分，因为 V2 必须基于简历原文，不允许凭空编造。
        if (!StringUtils.hasText(resume.getRawText()) || STATUS_PARSE_FAILED.equals(resume.getStatus())) {
            throw new BizException("当前简历没有可用的解析文本，请先上传可复制文字的 PDF、DOC 或 DOCX 简历。");
        }

        // 4. 先执行规则评分，得到稳定、可复现的初始分。
        //    注意：这一步不是最终结果；如果大模型可用，后面必须让 AI 基于简历原文重新参与评分。
        ResumeScoreRuleEngine.RuleScoreResult ruleScore = resumeScoreRuleEngine.calculate(resume.getRawText(), targetPosition);

        // 5. 统一走数据库模型网关。
        //    模型、Prompt、超时、重试、熔断和调用日志都由后台配置，不再依赖 application-local.yml 的 job.ai。
        ResumeScoreRuleEngine.RuleScoreResult scoreResult = calculateLlmScoreNow(
                userId,
                resumeId,
                resume.getRawText(),
                targetPosition,
                ruleScore
        );

        // 6. 保存评分记录。老字段继续写入，完整 V2 结构存 score_json。
        Date now = new Date();
        JobResumeScoreRecord record = new JobResumeScoreRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setTargetPosition(trimToNull(targetPosition));

        ResumeScoreRuleEngine.ScoreBreakdown breakdown = scoreResult.getScoreBreakdown();
        record.setTotalScore(toDecimal(scoreResult.getOverallScore()));
        record.setBasicInfoScore(toDecimal(breakdown.getBasicInfoScore()));
        record.setEducationScore(toDecimal(breakdown.getEducationScore()));
        record.setSkillScore(toDecimal(breakdown.getSkillsScore()));
        record.setProjectScore(toDecimal(breakdown.getProjectExperienceScore()));
        record.setExperienceScore(toDecimal(breakdown.getWorkExperienceScore()));
        record.setExpressionScore(toDecimal(breakdown.getFormatScore()));

        record.setAdvantage(joinLines(scoreResult.getStrengths()));
        record.setProblem(joinLines(buildLegacyProblems(scoreResult)));
        record.setSuggestion(joinLines(scoreResult.getImprovementSuggestions()));
        record.setScoreJson(toJson(scoreResult));

        record.setIsDeleted(NOT_DELETED);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        save(record);

        // 7. 同步更新 resume 表上的 score 和状态，方便简历列表直接展示最近总分。
        resume.setScore(toDecimal(scoreResult.getOverallScore()));
        resume.setStatus(STATUS_SCORED);
        resume.setUpdateTime(now);
        jobResumeService.updateById(resume);

        return ResumeScoreVO.from(record);
    }

    /**
     * 查询当前用户某份简历最近一次评分记录。
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
     * 立即调用大模型参与评分。
     * 说明:
     * 1. 用户明确要求“评分结果必须体现 AI 参与”，所以这里不再把规则评分提前返回成最终结果。
     * 2. 如果 AI 成功返回，就合并 AI 的维度分、分析、问题和建议，并标记 SUCCESS。
     * 3. 如果 AI 调用失败，会保留规则分，但明确标记 FAILED，并把错误原因写入 score_json 和前端。
     */
    private ResumeScoreRuleEngine.RuleScoreResult calculateLlmScoreNow(
            Long userId,
            Long resumeId,
            String rawText,
            String targetPosition,
            ResumeScoreRuleEngine.RuleScoreResult ruleScore
    ) {
        try {
            String prompt = buildLlmPrompt(rawText, targetPosition, ruleScore);
            String response = aiModelGatewayService.chat(
                    AI_SCENE_RESUME_SCORE,
                    buildResumeScoreVariables(rawText, targetPosition, ruleScore),
                    prompt,
                    userId,
                    buildResumeScoreTraceId(userId, resumeId)
            );
            ResumeScoreRuleEngine.RuleScoreResult llmResult = parseLlmResult(response);
            ResumeScoreRuleEngine.RuleScoreResult mergedResult = mergeLlmScore(ruleScore, llmResult);
            mergedResult.setLlmStatus("SUCCESS");
            mergedResult.setLlmError(null);
            return mergedResult;
        } catch (Exception exception) {
            ruleScore.setScoringMode("RULE_SCORE_LLM_FAILED");
            ruleScore.setLlmStatus("FAILED");
            ruleScore.setLlmError(shortMessage(exception));
            return ruleScore;
        }
    }

    /**
     * 构造简历评分 Prompt 变量。
     *
     * 方法步骤:
     * 1. 把求职方向、规则评分摘要、简历原文拆成独立变量，方便后台 Prompt 模板引用。
     * 2. 同时保留 fullPrompt，避免第一版模板只想直接使用完整用户消息。
     * 3. 简历原文在进入变量前先截断，防止后台模板误引用未截断文本导致 token 过大。
     */
    private Map<String, Object> buildResumeScoreVariables(
            String rawText,
            String targetPosition,
            ResumeScoreRuleEngine.RuleScoreResult ruleScore
    ) {
        String safeTargetPosition = StringUtils.hasText(targetPosition) ? targetPosition.trim() : "未填写";
        String ruleScoreSummary = buildRuleScoreSummary(ruleScore);
        String resumeText = truncate(rawText, LLM_RESUME_TEXT_LIMIT);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("targetPosition", safeTargetPosition);
        variables.put("target_position", safeTargetPosition);
        variables.put("ruleScoreSummary", ruleScoreSummary);
        variables.put("rule_score_summary", ruleScoreSummary);
        variables.put("resumeText", resumeText);
        variables.put("resume_text", resumeText);
        variables.put("fullPrompt", buildLlmPrompt(rawText, targetPosition, ruleScore));
        variables.put("full_prompt", variables.get("fullPrompt"));
        variables.put("jsonFormat", "只输出 JSON 对象，不要 Markdown，不要解释文本。");
        variables.put("json_format", variables.get("jsonFormat"));
        return variables;
    }

    private String buildResumeScoreTraceId(Long userId, Long resumeId) {
        return "resume_score_" + userId + "_" + resumeId + "_" + System.currentTimeMillis();
    }

    /**
     * 构造给大模型的 Prompt。
     * 说明:
     * 1. 这里不再传完整 ruleScore JSON，避免把大量规则解释、建议重复塞进上下文。
     * 2. 大模型作为“第二评分员”可以给出自己的维度分，后端再做加权合并。
     * 3. 要求列表最多 5 条，是为了降低输出长度和 JSON 解析失败概率。
     */
    private String buildLlmPrompt(String rawText, String targetPosition, ResumeScoreRuleEngine.RuleScoreResult ruleScore) {
        return """
                按简历原文重新评分，输出 JSON，不要 Markdown。
                要求:
                1. 八个维度顺序必须与规则摘要 dimensions 一致，maxScore 不变，score 不能超过 maxScore。
                2. 不要照抄规则分，要根据简历原文独立判断。
                3. dimensions.reason 每条不超过 30 字。
                4. strengths/weaknesses/riskPoints/improvementSuggestions 每类最多 3 条，每条不超过 40 字。
                5. overallScore 等于 dimensions.score 之和。
                6. dimensions 元素字段固定为 dimensionName、score、maxScore、reason、issues、suggestions。
                7. scoreBreakdown 字段固定为 basicInfoScore、careerGoalScore、educationScore、skillsScore、projectExperienceScore、workExperienceScore、quantifiedImpactScore、formatScore。
                
                用户填写的求职方向:
                %s
                
                规则评分摘要:
                %s
                
                简历原文:
                %s
                """.formatted(
                StringUtils.hasText(targetPosition) ? targetPosition.trim() : "未填写",
                buildRuleScoreSummary(ruleScore),
                truncate(rawText, LLM_RESUME_TEXT_LIMIT)
        );
    }

    /**
     * 构造更短的规则评分摘要。
     * 说明: 大模型只需要知道规则分和每个维度满分，不需要拿到所有本地建议文本。
     */
    private String buildRuleScoreSummary(ResumeScoreRuleEngine.RuleScoreResult ruleScore) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("overallScore", ruleScore.getOverallScore());

        List<Map<String, Object>> dimensionSummaries = new ArrayList<>();
        for (ResumeScoreRuleEngine.ScoreDimension dimension : nonNullList(ruleScore.getDimensions())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", dimension.getDimensionName());
            item.put("ruleScore", dimension.getScore());
            item.put("maxScore", dimension.getMaxScore());
            dimensionSummaries.add(item);
        }
        summary.put("dimensions", dimensionSummaries);
        return toJson(summary);
    }

    /**
     * 解析大模型返回的 JSON。
     */
    private ResumeScoreRuleEngine.RuleScoreResult parseLlmResult(String response) throws Exception {
        String json = extractJson(response);
        return objectMapper.readValue(json, ResumeScoreRuleEngine.RuleScoreResult.class);
    }

    /**
     * 从模型响应中提取 JSON 对象。
     * 说明: 有些模型会错误包一层 ```json 代码块，这里做兼容清洗。
     */
    private String extractJson(String response) {
        if (!StringUtils.hasText(response)) {
            throw new IllegalArgumentException("模型未返回内容");
        }

        String cleaned = response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型未返回合法 JSON: " + cleaned);
        }
        return cleaned.substring(start, end + 1);
    }

    /**
     * 合并 LLM 评分。
     * 说明:
     * 1. LLM 不是只写文案，它会给出自己的八维分数。
     * 2. 后端用加权合并控制最终分，规则分占 65%，模型分占 35%。
     * 3. 单个维度最多只允许被模型拉动该维度满分的 20%，防止模型一次性把分数改得过猛。
     */
    private ResumeScoreRuleEngine.RuleScoreResult mergeLlmScore(
            ResumeScoreRuleEngine.RuleScoreResult ruleScore,
            ResumeScoreRuleEngine.RuleScoreResult llmResult
    ) {
        List<ResumeScoreRuleEngine.ScoreDimension> llmDimensions = resolveLlmDimensions(ruleScore.getDimensions(), llmResult);
        ruleScore.setDimensions(mergeDimensions(ruleScore.getDimensions(), llmDimensions));
        ruleScore.setScoreBreakdown(buildBreakdownFromDimensions(ruleScore.getDimensions()));
        ruleScore.setOverallScore(sumDimensionScore(ruleScore.getDimensions()));
        ruleScore.setStrengths(preferLlmList(llmResult.getStrengths(), ruleScore.getStrengths()));
        ruleScore.setWeaknesses(preferLlmList(llmResult.getWeaknesses(), ruleScore.getWeaknesses()));
        ruleScore.setRiskPoints(preferLlmList(llmResult.getRiskPoints(), ruleScore.getRiskPoints()));
        ruleScore.setImprovementSuggestions(preferLlmList(llmResult.getImprovementSuggestions(), ruleScore.getImprovementSuggestions()));

        if (StringUtils.hasText(llmResult.getSummary())) {
            ruleScore.setSummary(llmResult.getSummary().trim());
        }

        ruleScore.setScoreVersion("V2");
        ruleScore.setScoringMode("RULE_LLM_WEIGHTED_SCORE");
        ruleScore.setLevel(resolveLevel(ruleScore.getOverallScore()));
        return ruleScore;
    }

    /**
     * 解析模型返回的维度分。
     * 说明:
     * 1. 正常情况下模型会返回 dimensions，里面包含每个维度的 score 和 reason。
     * 2. 有些模型为了省输出可能只返回 scoreBreakdown；这里把 scoreBreakdown 转成 dimensions，仍然让模型分参与合并。
     * 3. 这样可以降低“模型明明返回了分数，但后端没用上”的概率。
     */
    private List<ResumeScoreRuleEngine.ScoreDimension> resolveLlmDimensions(
            List<ResumeScoreRuleEngine.ScoreDimension> ruleDimensions,
            ResumeScoreRuleEngine.RuleScoreResult llmResult
    ) {
        if (llmResult.getDimensions() != null && !llmResult.getDimensions().isEmpty()) {
            return llmResult.getDimensions();
        }

        ResumeScoreRuleEngine.ScoreBreakdown breakdown = llmResult.getScoreBreakdown();
        if (breakdown == null) {
            return List.of();
        }

        List<Integer> scores = List.of(
                valueOrZero(breakdown.getBasicInfoScore()),
                valueOrZero(breakdown.getCareerGoalScore()),
                valueOrZero(breakdown.getEducationScore()),
                valueOrZero(breakdown.getSkillsScore()),
                valueOrZero(breakdown.getProjectExperienceScore()),
                valueOrZero(breakdown.getWorkExperienceScore()),
                valueOrZero(breakdown.getQuantifiedImpactScore()),
                valueOrZero(breakdown.getFormatScore())
        );

        List<ResumeScoreRuleEngine.ScoreDimension> dimensions = new ArrayList<>();
        List<ResumeScoreRuleEngine.ScoreDimension> safeRuleDimensions = nonNullList(ruleDimensions);
        for (int index = 0; index < safeRuleDimensions.size() && index < scores.size(); index++) {
            ResumeScoreRuleEngine.ScoreDimension ruleDimension = safeRuleDimensions.get(index);
            ResumeScoreRuleEngine.ScoreDimension dimension = new ResumeScoreRuleEngine.ScoreDimension();
            dimension.setDimensionName(ruleDimension.getDimensionName());
            dimension.setScore(scores.get(index));
            dimension.setMaxScore(ruleDimension.getMaxScore());
            dimension.setReason(ruleDimension.getReason());
            dimension.setIssues(ruleDimension.getIssues());
            dimension.setSuggestions(ruleDimension.getSuggestions());
            dimensions.add(dimension);
        }
        return dimensions;
    }

    private Integer valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<ResumeScoreRuleEngine.ScoreDimension> mergeDimensions(
            List<ResumeScoreRuleEngine.ScoreDimension> ruleDimensions,
            List<ResumeScoreRuleEngine.ScoreDimension> llmDimensions
    ) {
        if (llmDimensions == null || llmDimensions.isEmpty()) {
            return ruleDimensions;
        }

        Map<String, ResumeScoreRuleEngine.ScoreDimension> llmMap = new LinkedHashMap<>();
        for (ResumeScoreRuleEngine.ScoreDimension dimension : llmDimensions) {
            if (StringUtils.hasText(dimension.getDimensionName())) {
                llmMap.put(dimension.getDimensionName(), dimension);
            }
        }

        List<ResumeScoreRuleEngine.ScoreDimension> safeRuleDimensions = nonNullList(ruleDimensions);
        List<ResumeScoreRuleEngine.ScoreDimension> merged = new ArrayList<>();
        for (int index = 0; index < safeRuleDimensions.size(); index++) {
            ResumeScoreRuleEngine.ScoreDimension ruleDimension = safeRuleDimensions.get(index);
            ResumeScoreRuleEngine.ScoreDimension llmDimension = llmMap.get(ruleDimension.getDimensionName());
            if (llmDimension == null && llmDimensions.size() > index) {
                // 模型偶尔会把维度名写得不完全一致；八个维度顺序是固定的，因此可按位置兜底匹配。
                llmDimension = llmDimensions.get(index);
            }
            if (llmDimension == null) {
                merged.add(ruleDimension);
                continue;
            }

            ResumeScoreRuleEngine.ScoreDimension dimension = new ResumeScoreRuleEngine.ScoreDimension();
            dimension.setDimensionName(ruleDimension.getDimensionName());
            dimension.setScore(blendScore(ruleDimension.getScore(), llmDimension.getScore(), ruleDimension.getMaxScore()));
            dimension.setMaxScore(ruleDimension.getMaxScore());
            dimension.setReason(StringUtils.hasText(llmDimension.getReason()) ? llmDimension.getReason().trim() : ruleDimension.getReason());
            dimension.setIssues(preferLlmList(llmDimension.getIssues(), ruleDimension.getIssues()));
            dimension.setSuggestions(preferLlmList(llmDimension.getSuggestions(), ruleDimension.getSuggestions()));
            merged.add(dimension);
        }
        return merged;
    }

    /**
     * 合并单个维度分。
     * 说明:
     * 1. 先按权重计算 blended = 规则分 * 65% + 模型分 * 35%。
     * 2. 再限制模型对该维度的最大影响幅度，默认最多影响该维度满分的 20%。
     * 3. 最后再限制到 0~maxScore，保证不会出现负分或超满分。
     */
    private Integer blendScore(Integer ruleScore, Integer llmScore, Integer maxScore) {
        int safeMax = maxScore == null ? 0 : maxScore;
        int safeRuleScore = clamp(ruleScore, 0, safeMax);
        int safeLlmScore = clamp(llmScore, 0, safeMax);
        int weightedScore = (int) Math.round(safeRuleScore * (1 - LLM_SCORE_WEIGHT) + safeLlmScore * LLM_SCORE_WEIGHT);
        int maxAdjustment = Math.max(1, (int) Math.round(safeMax * 0.2));
        return clamp(weightedScore, safeRuleScore - maxAdjustment, safeRuleScore + maxAdjustment);
    }

    private int clamp(Integer value, int min, Integer max) {
        int safeValue = value == null ? min : value;
        int safeMax = max == null ? safeValue : max;
        return Math.max(min, Math.min(safeValue, safeMax));
    }

    private int sumDimensionScore(List<ResumeScoreRuleEngine.ScoreDimension> dimensions) {
        return nonNullList(dimensions).stream()
                .map(ResumeScoreRuleEngine.ScoreDimension::getScore)
                .mapToInt(score -> score == null ? 0 : score)
                .sum();
    }

    private ResumeScoreRuleEngine.ScoreBreakdown buildBreakdownFromDimensions(List<ResumeScoreRuleEngine.ScoreDimension> dimensions) {
        ResumeScoreRuleEngine.ScoreBreakdown breakdown = new ResumeScoreRuleEngine.ScoreBreakdown();
        List<ResumeScoreRuleEngine.ScoreDimension> safeDimensions = nonNullList(dimensions);
        breakdown.setBasicInfoScore(dimensionScore(safeDimensions, 0));
        breakdown.setCareerGoalScore(dimensionScore(safeDimensions, 1));
        breakdown.setEducationScore(dimensionScore(safeDimensions, 2));
        breakdown.setSkillsScore(dimensionScore(safeDimensions, 3));
        breakdown.setProjectExperienceScore(dimensionScore(safeDimensions, 4));
        breakdown.setWorkExperienceScore(dimensionScore(safeDimensions, 5));
        breakdown.setQuantifiedImpactScore(dimensionScore(safeDimensions, 6));
        breakdown.setFormatScore(dimensionScore(safeDimensions, 7));
        return breakdown;
    }

    private Integer dimensionScore(List<ResumeScoreRuleEngine.ScoreDimension> dimensions, int index) {
        if (dimensions == null || dimensions.size() <= index || dimensions.get(index).getScore() == null) {
            return 0;
        }
        return dimensions.get(index).getScore();
    }

    /**
     * 兼容旧字段 problem。
     * 说明: V2 已经拆成 weaknesses 和 riskPoints，旧字段用两者合并，旧前端也能看到问题。
     */
    private List<String> buildLegacyProblems(ResumeScoreRuleEngine.RuleScoreResult scoreResult) {
        List<String> problems = new ArrayList<>();
        problems.addAll(nonNullList(scoreResult.getWeaknesses()));
        problems.addAll(nonNullList(scoreResult.getRiskPoints()));
        return problems.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(8)
                .toList();
    }

    private List<String> preferLlmList(List<String> llmList, List<String> fallbackList) {
        List<String> cleaned = nonNullList(llmList).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        return cleaned.isEmpty() ? nonNullList(fallbackList) : cleaned;
    }

    private <T> List<T> nonNullList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private BigDecimal toDecimal(Integer value) {
        return BigDecimal.valueOf(value == null ? 0 : value);
    }

    private String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return String.join("\n", lines);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n\n[后续内容因长度限制已截断]";
    }

    private String shortMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private String resolveLevel(Integer score) {
        int value = score == null ? 0 : score;
        if (value >= 90) {
            return "优秀";
        }
        if (value >= 80) {
            return "良好";
        }
        if (value >= 70) {
            return "一般";
        }
        if (value >= 60) {
            return "较弱";
        }
        return "需要重写";
    }

}
