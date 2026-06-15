package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.agent.ResumeScoreAssistant;
import com.job.bootstrap.mapper.JobResumeScoreRecordMapper;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.resume.ResumeScoreRuleEngine;
import com.job.common.entity.resume.JobResume;
import com.job.common.entity.resume.JobResumeScoreRecord;
import com.job.common.vo.resume.ResumeScoreVO;
import com.job.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 作者:hfj
 * 功能:简历 AI 评分业务实现
 * 日期:2026/6/15
 *
 * V2 设计说明:
 * 1. 先用 ResumeScoreRuleEngine 计算稳定分数，保证评分可解释、可测试、可重复。
 * 2. 再调用 ResumeScoreAssistant，让大模型基于简历原文补充更自然的优势、不足、风险点和建议。
 * 3. 最终分数仍以规则引擎为准，大模型不能随意改分，避免同一份简历多次评分结果漂移。
 * 4. score_json 保存完整 V2 结构，老字段继续写入，兼容当前数据库和旧前端字段。
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
     * 简历解析失败状态。
     */
    private static final String STATUS_PARSE_FAILED = "PARSE_FAILED";

    /**
     * 未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 送给模型的简历最大字符数。
     * 说明: 简历过长时先截断，避免一次模型调用超过上下文长度。
     */
    private static final int LLM_RESUME_TEXT_LIMIT = 12000;

    /**
     * 简历评分大模型辅助点评最多等待时间。
     * 说明: 评分接口是用户点击按钮触发的同步请求，不能因为模型供应商网络抖动一直卡住。
     * 超过这个时间就返回规则评分兜底结果，保证“重新评分”按钮稳定可用。
     */
    private static final long LLM_ANALYSIS_TIMEOUT_SECONDS = 12;

    private final JobResumeService jobResumeService;
    private final ResumeScoreRuleEngine resumeScoreRuleEngine;
    private final ObjectProvider<ResumeScoreAssistant> resumeScoreAssistantProvider;
    private final ObjectMapper objectMapper;

    /**
     * 简历评分 LLM 辅助分析线程池。
     * 说明: 单独线程池可以给模型调用加超时控制，不影响主业务线程及时返回规则评分结果。
     */
    private final ExecutorService resumeScoreLlmExecutor = Executors.newFixedThreadPool(
            2,
            new ResumeScoreThreadFactory()
    );

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

        // 4. 执行 V2 评分: 规则算分 + LLM 补充解释 + 分数校验合并。
        ResumeScoreRuleEngine.RuleScoreResult scoreResult = calculateV2Score(resume.getRawText(), targetPosition);

        // 5. 保存评分记录。老字段继续写入，完整 V2 结构存 score_json。
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

        // 6. 同步更新 resume 表上的 score 和状态，方便简历列表直接展示最近总分。
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
     * V2 核心评分流程。
     *
     * @param rawText 简历原文
     * @param targetPosition 求职方向，可为空
     * @return 合并后的最终评分结果
     */
    private ResumeScoreRuleEngine.RuleScoreResult calculateV2Score(String rawText, String targetPosition) {
        // 1. 规则引擎先产出稳定分数和兜底解释。
        ResumeScoreRuleEngine.RuleScoreResult ruleScore = resumeScoreRuleEngine.calculate(rawText, targetPosition);

        // 2. 如果当前环境没有注册 ResumeScoreAssistant，则直接返回规则结果，便于测试和本地离线运行。
        ResumeScoreAssistant assistant = resumeScoreAssistantProvider.getIfAvailable();
        if (assistant == null) {
            ruleScore.setLlmStatus("SKIPPED");
            ruleScore.setLlmError("未注册 ResumeScoreAssistant，已使用规则评分兜底。");
            return ruleScore;
        }

        try {
            // 3. 大模型只负责补充解释，不负责最终定分。
            String prompt = buildLlmPrompt(rawText, targetPosition, ruleScore);
            String response = callAssistantWithTimeout(assistant, prompt);
            ResumeScoreRuleEngine.RuleScoreResult llmResult = parseLlmResult(response);
            ResumeScoreRuleEngine.RuleScoreResult mergedResult = mergeLlmExplanation(ruleScore, llmResult);
            mergedResult.setLlmStatus("SUCCESS");
            mergedResult.setLlmError(null);
            return mergedResult;
        } catch (Exception exception) {
            /*
             * 4. 模型调用失败时不能影响用户评分。
             *    这里记录失败原因到 score_json，前端仍能看到规则评分和本地建议。
             */
            ruleScore.setLlmStatus("FAILED");
            ruleScore.setLlmError(shortMessage(exception));
            return ruleScore;
        }
    }

    /**
     * 带超时调用简历评分大模型。
     * 说明:
     * 1. 火山方舟或其他 OpenAI 兼容接口偶尔会出现 Request cancelled / timeout。
     * 2. 如果直接在 HTTP 请求线程里等待模型，前端“重新评分”会变成失败。
     * 3. 这里把模型调用放到独立线程，并设置短超时；超时后抛出异常，外层会保存规则评分兜底。
     */
    private String callAssistantWithTimeout(ResumeScoreAssistant assistant, String prompt) throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> assistant.analyze(prompt),
                resumeScoreLlmExecutor
        );

        try {
            return future.get(LLM_ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException("AI 简历点评超过 " + LLM_ANALYSIS_TIMEOUT_SECONDS + " 秒未返回，已使用规则评分兜底。", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 简历点评线程被中断，已使用规则评分兜底。", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception actualException) {
                throw actualException;
            }
            throw new IllegalStateException("AI 简历点评调用失败，已使用规则评分兜底。", cause);
        }
    }

    /**
     * 构造给大模型的 Prompt。
     * 说明: Prompt 中同时提供规则评分结果和简历原文，让模型在不改分的前提下补充证据化分析。
     */
    private String buildLlmPrompt(String rawText, String targetPosition, ResumeScoreRuleEngine.RuleScoreResult ruleScore) {
        return """
                请基于下面的规则评分结果和简历原文，输出符合系统提示词要求的 JSON。
                
                重要约束:
                1. 你必须保留规则评分中的 overallScore 和 scoreBreakdown，不能改分。
                2. 你可以优化 dimensions.reason、issues、suggestions。
                3. strengths、weaknesses、riskPoints、improvementSuggestions 必须基于简历原文，不允许编造。
                4. 如果某项没有证据，请写“简历中未找到相关证据”。
                
                用户填写的求职方向:
                %s
                
                规则评分结果:
                %s
                
                简历原文:
                %s
                """.formatted(
                StringUtils.hasText(targetPosition) ? targetPosition.trim() : "未填写",
                toJson(ruleScore),
                truncate(rawText, LLM_RESUME_TEXT_LIMIT)
        );
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
     * 合并 LLM 解释。
     * 说明: 这里故意保留 ruleScore 的所有分数，只接受 LLM 的文案字段，避免模型改分。
     */
    private ResumeScoreRuleEngine.RuleScoreResult mergeLlmExplanation(
            ResumeScoreRuleEngine.RuleScoreResult ruleScore,
            ResumeScoreRuleEngine.RuleScoreResult llmResult
    ) {
        ruleScore.setDimensions(mergeDimensions(ruleScore.getDimensions(), llmResult.getDimensions()));
        ruleScore.setStrengths(preferLlmList(llmResult.getStrengths(), ruleScore.getStrengths()));
        ruleScore.setWeaknesses(preferLlmList(llmResult.getWeaknesses(), ruleScore.getWeaknesses()));
        ruleScore.setRiskPoints(preferLlmList(llmResult.getRiskPoints(), ruleScore.getRiskPoints()));
        ruleScore.setImprovementSuggestions(preferLlmList(llmResult.getImprovementSuggestions(), ruleScore.getImprovementSuggestions()));

        if (StringUtils.hasText(llmResult.getSummary())) {
            ruleScore.setSummary(llmResult.getSummary().trim());
        }

        /*
         * 强制还原规则分数。
         * 即使模型返回了不同分数，也不允许覆盖 ruleScore 的 final score。
         */
        ruleScore.setScoreVersion("V2");
        ruleScore.setScoringMode("RULE_WITH_LLM_EXPLANATION");
        ruleScore.setLevel(resolveLevel(ruleScore.getOverallScore()));
        return ruleScore;
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

        List<ResumeScoreRuleEngine.ScoreDimension> merged = new ArrayList<>();
        for (ResumeScoreRuleEngine.ScoreDimension ruleDimension : ruleDimensions) {
            ResumeScoreRuleEngine.ScoreDimension llmDimension = llmMap.get(ruleDimension.getDimensionName());
            if (llmDimension == null) {
                merged.add(ruleDimension);
                continue;
            }

            ResumeScoreRuleEngine.ScoreDimension dimension = new ResumeScoreRuleEngine.ScoreDimension();
            dimension.setDimensionName(ruleDimension.getDimensionName());
            dimension.setScore(ruleDimension.getScore());
            dimension.setMaxScore(ruleDimension.getMaxScore());
            dimension.setReason(StringUtils.hasText(llmDimension.getReason()) ? llmDimension.getReason().trim() : ruleDimension.getReason());
            dimension.setIssues(preferLlmList(llmDimension.getIssues(), ruleDimension.getIssues()));
            dimension.setSuggestions(preferLlmList(llmDimension.getSuggestions(), ruleDimension.getSuggestions()));
            merged.add(dimension);
        }
        return merged;
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

    private List<String> nonNullList(List<String> list) {
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

    /**
     * 应用关闭时释放简历评分 LLM 线程池。
     */
    @PreDestroy
    public void destroy() {
        resumeScoreLlmExecutor.shutdownNow();
    }

    /**
     * 简历评分 LLM 线程工厂。
     * 说明: 线程名带业务含义，后续看日志或线程 dump 时更容易定位。
     */
    private static class ResumeScoreThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "resume-score-llm-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
