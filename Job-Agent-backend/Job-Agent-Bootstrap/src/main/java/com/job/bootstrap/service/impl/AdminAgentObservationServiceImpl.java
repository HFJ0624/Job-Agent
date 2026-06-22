package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentObservationAlertRecordMapper;
import com.job.bootstrap.mapper.AgentObservationAlertRuleMapper;
import com.job.bootstrap.mapper.AgentObservationEventMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.mapper.AgentTraceRetentionPolicyMapper;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.service.AdminAgentObservationService;
import com.job.common.dto.agent.AgentObservationAlertRecordQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleQueryDTO;
import com.job.common.dto.agent.AgentObservationAlertRuleSaveDTO;
import com.job.common.dto.agent.AgentObservationDashboardQueryDTO;
import com.job.common.dto.agent.AgentObservationEventQueryDTO;
import com.job.common.dto.agent.AgentTraceRetentionPolicySaveDTO;
import com.job.common.entity.agent.AgentObservationAlertRecord;
import com.job.common.entity.agent.AgentObservationAlertRule;
import com.job.common.entity.agent.AgentObservationEvent;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.entity.agent.AgentTraceRetentionPolicy;
import com.job.common.entity.ai.AiModelCallLog;
import com.job.common.vo.agent.AgentObservationAlertRecordVO;
import com.job.common.vo.agent.AgentObservationAlertRuleVO;
import com.job.common.vo.agent.AgentObservationDashboardVO;
import com.job.common.vo.agent.AgentObservationEventVO;
import com.job.common.vo.agent.AgentObservationStatItemVO;
import com.job.common.vo.agent.AgentTraceRetentionPolicyVO;
import com.job.common.vo.agent.AgentTraceRetentionPreviewVO;
import com.job.enums.AgentObservationAlertRuleType;
import com.job.enums.AgentObservationAlertStatus;
import com.job.enums.AgentObservationConfigStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作者: hfj
 * 功能: 后台 Agent 统一观测查询、统计、告警和保留策略服务实现
 * 日期: 2026/6/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAgentObservationServiceImpl implements AdminAgentObservationService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int DEFAULT_DASHBOARD_DAYS = 7;
    private static final int DEFAULT_WINDOW_MINUTES = 10;
    private static final int DEFAULT_MIN_SAMPLE_COUNT = 1;
    private static final int DEFAULT_COOLDOWN_MINUTES = 30;
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_BATCH_SIZE = 5000;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentObservationEventMapper agentObservationEventMapper;
    private final AgentObservationAlertRuleMapper agentObservationAlertRuleMapper;
    private final AgentObservationAlertRecordMapper agentObservationAlertRecordMapper;
    private final AgentTraceRetentionPolicyMapper agentTraceRetentionPolicyMapper;
    private final AgentTraceLogMapper agentTraceLogMapper;
    private final AiModelCallLogMapper aiModelCallLogMapper;

    /**
     * 分页查询观测事件。
     *
     * 方法步骤:
     * 1. 先限制分页参数，防止后台一次拉取过多观测明细。
     * 2. 按可观测维度动态拼接筛选条件。
     * 3. 按创建时间倒序返回，方便管理员优先看到最新问题。
     *
     * @param query 查询条件
     * @return 观测事件分页
     */
    @Override
    public IPage<AgentObservationEventVO> pageEvents(AgentObservationEventQueryDTO query) {
        AgentObservationEventQueryDTO safeQuery = query == null ? new AgentObservationEventQueryDTO() : query;
        Page<AgentObservationEvent> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<AgentObservationEvent> wrapper = buildWrapper(safeQuery);
        wrapper.orderByDesc(AgentObservationEvent::getCreateTime);
        return agentObservationEventMapper.selectPage(page, wrapper).convert(AgentObservationEventVO::from);
    }

    /**
     * 查询观测事件详情。
     *
     * @param id 事件 ID
     * @return 观测事件详情
     */
    @Override
    public AgentObservationEventVO getDetail(Long id) {
        AgentObservationEvent event = agentObservationEventMapper.selectById(id);
        if (event == null || Integer.valueOf(DELETED).equals(event.getIsDeleted())) {
            throw new BizException("Agent 观测事件不存在");
        }
        return AgentObservationEventVO.from(event);
    }

    /**
     * 查询 Agent 观测看板。
     *
     * 方法步骤:
     * 1. 默认查询最近 7 天，避免没有筛选条件时扫描全表。
     * 2. 一次性读取时间范围内的观测事件，在 Java 内存中完成第一版聚合。
     * 3. 看板只展示 Top 级别结果，明细仍然走分页接口查询。
     *
     * @param query 查询条件
     * @return 看板指标
     */
    @Override
    public AgentObservationDashboardVO dashboard(AgentObservationDashboardQueryDTO query) {
        List<AgentObservationEvent> events = loadDashboardEvents(query);
        long total = events.size();
        long success = countByStatus(events, "SUCCESS");
        long failed = countByStatus(events, "FAILED");
        long blocked = countByStatus(events, "BLOCKED");
        long skipped = countByStatus(events, "SKIPPED");

        AgentObservationDashboardVO vo = new AgentObservationDashboardVO();
        vo.setTotalEvents(total);
        vo.setSuccessEvents(success);
        vo.setFailedEvents(failed);
        vo.setBlockedEvents(blocked);
        vo.setSkippedEvents(skipped);
        vo.setSuccessRate(percent(success, total));
        vo.setAvgDurationMs(avgDuration(events));
        vo.setTotalTokens(sumTokens(events));
        vo.setTotalCost(sumCost(events));
        vo.setEventTypeStats(groupStats(events, AgentObservationEvent::getEventType, total, 10));
        vo.setFailureStats(buildFailureStats(events));
        vo.setSlowModelStats(buildSlowStats(events, "MODEL"));
        vo.setSlowToolStats(buildSlowToolStats(events));
        vo.setRecentAlerts(loadRecentAlerts());
        return vo;
    }

    /**
     * 查询失败分类统计。
     *
     * @param query 查询条件
     * @return 失败分类统计
     */
    @Override
    public List<AgentObservationStatItemVO> failureStats(AgentObservationDashboardQueryDTO query) {
        return buildFailureStats(loadDashboardEvents(query));
    }

    @Override
    public IPage<AgentObservationAlertRuleVO> pageAlertRules(AgentObservationAlertRuleQueryDTO query) {
        AgentObservationAlertRuleQueryDTO safeQuery = query == null ? new AgentObservationAlertRuleQueryDTO() : query;
        Page<AgentObservationAlertRule> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<AgentObservationAlertRule> wrapper = new LambdaQueryWrapper<AgentObservationAlertRule>()
                .eq(AgentObservationAlertRule::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(safeQuery.getRuleName())) {
            wrapper.like(AgentObservationAlertRule::getRuleName, safeQuery.getRuleName().trim());
        }
        if (StringUtils.hasText(safeQuery.getRuleType())) {
            wrapper.eq(AgentObservationAlertRule::getRuleType, safeQuery.getRuleType().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(AgentObservationAlertRule::getStatus, safeQuery.getStatus().trim());
        }
        wrapper.orderByDesc(AgentObservationAlertRule::getCreateTime);
        return agentObservationAlertRuleMapper.selectPage(page, wrapper).convert(AgentObservationAlertRuleVO::from);
    }

    @Override
    public AgentObservationAlertRuleVO createAlertRule(AgentObservationAlertRuleSaveDTO request) {
        AgentObservationAlertRule rule = new AgentObservationAlertRule();
        fillAlertRule(rule, request);
        Date now = new Date();
        rule.setIsDeleted(NOT_DELETED);
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        agentObservationAlertRuleMapper.insert(rule);
        return AgentObservationAlertRuleVO.from(rule);
    }

    @Override
    public AgentObservationAlertRuleVO updateAlertRule(Long id, AgentObservationAlertRuleSaveDTO request) {
        AgentObservationAlertRule rule = loadAlertRule(id);
        fillAlertRule(rule, request);
        rule.setUpdateTime(new Date());
        agentObservationAlertRuleMapper.updateById(rule);
        return AgentObservationAlertRuleVO.from(rule);
    }

    @Override
    public void deleteAlertRule(Long id) {
        AgentObservationAlertRule rule = loadAlertRule(id);
        rule.setIsDeleted(DELETED);
        rule.setUpdateTime(new Date());
        agentObservationAlertRuleMapper.updateById(rule);
    }

    /**
     * 手动评估所有启用告警规则。
     *
     * 方法步骤:
     * 1. 只读取 ACTIVE 且未逻辑删除的规则。
     * 2. 每条规则按自己的时间窗口计算指标值。
     * 3. 指标超过阈值且不在冷却期内时，写入一条站内告警记录。
     *
     * @return 本次新增的告警记录
     */
    @Override
    public List<AgentObservationAlertRecordVO> evaluateAlertRules() {
        List<AgentObservationAlertRule> rules = agentObservationAlertRuleMapper.selectList(
                new LambdaQueryWrapper<AgentObservationAlertRule>()
                        .eq(AgentObservationAlertRule::getStatus, AgentObservationConfigStatus.ACTIVE.name())
                        .eq(AgentObservationAlertRule::getIsDeleted, NOT_DELETED)
        );

        Date now = new Date();
        List<AgentObservationAlertRecordVO> alerts = new ArrayList<>();
        for (AgentObservationAlertRule rule : rules) {
            AlertMetric metric = calculateAlertMetric(rule, now);
            rule.setLastEvaluateTime(now);

            if (metric.thresholdMatched()
                    && metric.sampleCount() >= safePositive(rule.getMinSampleCount(), DEFAULT_MIN_SAMPLE_COUNT)
                    && !inCooldown(rule, now)) {
                AgentObservationAlertRecord record = buildAlertRecord(rule, metric, now);
                agentObservationAlertRecordMapper.insert(record);
                rule.setLastAlertTime(now);
                alerts.add(AgentObservationAlertRecordVO.from(record));
            }

            rule.setUpdateTime(now);
            agentObservationAlertRuleMapper.updateById(rule);
        }
        return alerts;
    }

    @Override
    public IPage<AgentObservationAlertRecordVO> pageAlertRecords(AgentObservationAlertRecordQueryDTO query) {
        AgentObservationAlertRecordQueryDTO safeQuery = query == null ? new AgentObservationAlertRecordQueryDTO() : query;
        Page<AgentObservationAlertRecord> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<AgentObservationAlertRecord> wrapper = new LambdaQueryWrapper<AgentObservationAlertRecord>()
                .eq(AgentObservationAlertRecord::getIsDeleted, NOT_DELETED);
        if (safeQuery.getRuleId() != null) {
            wrapper.eq(AgentObservationAlertRecord::getRuleId, safeQuery.getRuleId());
        }
        if (StringUtils.hasText(safeQuery.getRuleType())) {
            wrapper.eq(AgentObservationAlertRecord::getRuleType, safeQuery.getRuleType().trim());
        }
        if (StringUtils.hasText(safeQuery.getAlertLevel())) {
            wrapper.eq(AgentObservationAlertRecord::getAlertLevel, safeQuery.getAlertLevel().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(AgentObservationAlertRecord::getStatus, safeQuery.getStatus().trim());
        }
        if (StringUtils.hasText(safeQuery.getStartTime())) {
            wrapper.ge(AgentObservationAlertRecord::getCreateTime, safeQuery.getStartTime().trim());
        }
        if (StringUtils.hasText(safeQuery.getEndTime())) {
            wrapper.le(AgentObservationAlertRecord::getCreateTime, safeQuery.getEndTime().trim());
        }
        wrapper.orderByDesc(AgentObservationAlertRecord::getCreateTime);
        return agentObservationAlertRecordMapper.selectPage(page, wrapper).convert(AgentObservationAlertRecordVO::from);
    }

    @Override
    public AgentObservationAlertRecordVO updateAlertRecordStatus(Long id, String status) {
        AgentObservationAlertRecord record = agentObservationAlertRecordMapper.selectById(id);
        if (record == null || Integer.valueOf(DELETED).equals(record.getIsDeleted())) {
            throw new BizException("告警记录不存在");
        }
        AgentObservationAlertStatus alertStatus = parseAlertStatus(status);
        record.setStatus(alertStatus.name());
        record.setUpdateTime(new Date());
        agentObservationAlertRecordMapper.updateById(record);
        return AgentObservationAlertRecordVO.from(record);
    }

    @Override
    public List<AgentTraceRetentionPolicyVO> listRetentionPolicies() {
        return agentTraceRetentionPolicyMapper.selectList(new LambdaQueryWrapper<AgentTraceRetentionPolicy>()
                        .eq(AgentTraceRetentionPolicy::getIsDeleted, NOT_DELETED)
                        .orderByAsc(AgentTraceRetentionPolicy::getTargetTable)
                        .orderByDesc(AgentTraceRetentionPolicy::getCreateTime))
                .stream()
                .map(AgentTraceRetentionPolicyVO::from)
                .toList();
    }

    @Override
    public AgentTraceRetentionPolicyVO createRetentionPolicy(AgentTraceRetentionPolicySaveDTO request) {
        AgentTraceRetentionPolicy policy = new AgentTraceRetentionPolicy();
        fillRetentionPolicy(policy, request);
        Date now = new Date();
        policy.setIsDeleted(NOT_DELETED);
        policy.setCreateTime(now);
        policy.setUpdateTime(now);
        policy.setLastDeletedCount(0);
        agentTraceRetentionPolicyMapper.insert(policy);
        return AgentTraceRetentionPolicyVO.from(policy);
    }

    @Override
    public AgentTraceRetentionPolicyVO updateRetentionPolicy(Long id, AgentTraceRetentionPolicySaveDTO request) {
        AgentTraceRetentionPolicy policy = loadRetentionPolicy(id);
        fillRetentionPolicy(policy, request);
        policy.setUpdateTime(new Date());
        agentTraceRetentionPolicyMapper.updateById(policy);
        return AgentTraceRetentionPolicyVO.from(policy);
    }

    @Override
    public AgentTraceRetentionPreviewVO previewRetentionPolicy(Long id) {
        AgentTraceRetentionPolicy policy = loadRetentionPolicy(id);
        return previewPolicy(policy);
    }

    /**
     * 执行单条 Trace 保留策略。
     *
     * 方法步骤:
     * 1. 先根据 retentionDays 计算截止时间。
     * 2. 目标表必须命中后端白名单，避免前端传任意表名。
     * 3. 只做 is_deleted=1 逻辑删除，并限制 batchSize。
     *
     * @param id 策略 ID
     * @return 执行后的预览信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentTraceRetentionPreviewVO executeRetentionPolicy(Long id) {
        AgentTraceRetentionPolicy policy = loadRetentionPolicy(id);
        Date cutoffTime = retentionCutoff(policy);
        int deletedCount = executeLogicalDelete(policy.getTargetTable(), cutoffTime, safeBatchSize(policy.getBatchSize()));

        policy.setLastExecuteTime(new Date());
        policy.setLastDeletedCount(deletedCount);
        policy.setUpdateTime(new Date());
        agentTraceRetentionPolicyMapper.updateById(policy);
        return previewPolicy(policy);
    }

    /**
     * 每天凌晨执行维护任务。
     *
     * 说明:
     * 1. 告警评估和保留策略都属于可观测性后台维护，不参与用户请求主流程。
     * 2. 任何异常都只记录日志，避免定时任务中断影响应用启动或后续调度。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void runDailyObservationMaintenance() {
        try {
            evaluateAlertRules();
            executeActiveRetentionPolicies();
        } catch (Exception exception) {
            log.warn("Agent 观测维护任务执行失败，error={}", exception.getMessage(), exception);
        }
    }

    private void executeActiveRetentionPolicies() {
        List<AgentTraceRetentionPolicy> policies = agentTraceRetentionPolicyMapper.selectList(
                new LambdaQueryWrapper<AgentTraceRetentionPolicy>()
                        .eq(AgentTraceRetentionPolicy::getStatus, AgentObservationConfigStatus.ACTIVE.name())
                        .eq(AgentTraceRetentionPolicy::getIsDeleted, NOT_DELETED)
        );
        for (AgentTraceRetentionPolicy policy : policies) {
            executeRetentionPolicy(policy.getId());
        }
    }

    private List<AgentObservationEvent> loadDashboardEvents(AgentObservationDashboardQueryDTO query) {
        Date endTime = parseDateOrDefault(query == null ? null : query.getEndTime(), new Date());
        Date startTime = parseDateOrDefault(
                query == null ? null : query.getStartTime(),
                Date.from(LocalDateTime.now().minusDays(DEFAULT_DASHBOARD_DAYS).atZone(ZoneId.systemDefault()).toInstant())
        );

        LambdaQueryWrapper<AgentObservationEvent> wrapper = new LambdaQueryWrapper<AgentObservationEvent>()
                .eq(AgentObservationEvent::getIsDeleted, NOT_DELETED)
                .ge(AgentObservationEvent::getCreateTime, startTime)
                .le(AgentObservationEvent::getCreateTime, endTime);

        if (query != null && StringUtils.hasText(query.getEventType())) {
            wrapper.eq(AgentObservationEvent::getEventType, query.getEventType().trim());
        }
        if (query != null && StringUtils.hasText(query.getModelCode())) {
            wrapper.eq(AgentObservationEvent::getModelCode, query.getModelCode().trim());
        }
        if (query != null && StringUtils.hasText(query.getToolName())) {
            wrapper.like(AgentObservationEvent::getToolName, query.getToolName().trim());
        }

        return agentObservationEventMapper.selectList(wrapper);
    }

    private List<AgentObservationAlertRecordVO> loadRecentAlerts() {
        Page<AgentObservationAlertRecord> page = new Page<>(1, 5);
        return agentObservationAlertRecordMapper.selectPage(page, new LambdaQueryWrapper<AgentObservationAlertRecord>()
                        .eq(AgentObservationAlertRecord::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentObservationAlertRecord::getCreateTime))
                .getRecords()
                .stream()
                .map(AgentObservationAlertRecordVO::from)
                .toList();
    }

    private List<AgentObservationStatItemVO> buildFailureStats(List<AgentObservationEvent> events) {
        List<AgentObservationEvent> failedEvents = events.stream()
                .filter(event -> "FAILED".equals(event.getStatus()) || "BLOCKED".equals(event.getStatus()))
                .filter(event -> StringUtils.hasText(event.getErrorCategory()))
                .filter(event -> !"NONE".equals(event.getErrorCategory()))
                .toList();
        return groupStats(failedEvents, AgentObservationEvent::getErrorCategory, failedEvents.size(), 10);
    }

    private List<AgentObservationStatItemVO> buildSlowStats(List<AgentObservationEvent> events, String eventType) {
        List<AgentObservationEvent> filteredEvents = events.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .filter(event -> event.getDurationMs() != null)
                .toList();
        return groupStats(filteredEvents, event -> firstText(event.getModelCode(), event.getEventName()), filteredEvents.size(), 5)
                .stream()
                .sorted(Comparator.comparing(AgentObservationStatItemVO::getAvgDurationMs, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<AgentObservationStatItemVO> buildSlowToolStats(List<AgentObservationEvent> events) {
        List<AgentObservationEvent> toolEvents = events.stream()
                .filter(event -> "TOOL".equals(event.getEventType()))
                .filter(event -> event.getDurationMs() != null)
                .toList();
        return groupStats(toolEvents, event -> firstText(event.getToolName(), event.getEventName()), toolEvents.size(), 5)
                .stream()
                .sorted(Comparator.comparing(AgentObservationStatItemVO::getAvgDurationMs, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
    }

    private List<AgentObservationStatItemVO> groupStats(
            List<AgentObservationEvent> events,
            StatKeyResolver resolver,
            long total,
            int limit
    ) {
        Map<String, List<AgentObservationEvent>> grouped = new LinkedHashMap<>();
        for (AgentObservationEvent event : events) {
            String key = resolver.resolve(event);
            if (!StringUtils.hasText(key)) {
                key = "UNKNOWN";
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        }

        return grouped.entrySet()
                .stream()
                .map(entry -> buildStatItem(entry.getKey(), entry.getValue(), total))
                .sorted(Comparator.comparing(AgentObservationStatItemVO::getCount).reversed())
                .limit(limit)
                .toList();
    }

    private AgentObservationStatItemVO buildStatItem(String name, List<AgentObservationEvent> events, long total) {
        return AgentObservationStatItemVO.builder()
                .name(name)
                .count((long) events.size())
                .ratio(percent(events.size(), total))
                .totalCost(sumCost(events))
                .totalTokens(sumTokens(events))
                .avgDurationMs(avgDuration(events))
                .maxDurationMs(maxDuration(events))
                .lastTime(lastTime(events))
                .build();
    }

    private AlertMetric calculateAlertMetric(AgentObservationAlertRule rule, Date now) {
        AgentObservationAlertRuleType ruleType = parseRuleType(rule.getRuleType());
        Date windowStart = Date.from(LocalDateTime.now()
                .minusMinutes(safePositive(rule.getWindowMinutes(), DEFAULT_WINDOW_MINUTES))
                .atZone(ZoneId.systemDefault())
                .toInstant());
        List<AgentObservationEvent> events = loadRuleWindowEvents(rule, windowStart, now);

        /*
         * 每种规则只计算自己关心的指标:
         * 1. 失败率需要总样本和失败样本。
         * 2. 次数类规则直接比较 count。
         * 3. 平均耗时和总成本只统计存在对应字段的事件。
         */
        return switch (ruleType) {
            case FAILURE_RATE -> {
                long total = events.size();
                long failed = events.stream()
                        .filter(event -> "FAILED".equals(event.getStatus()) || "BLOCKED".equals(event.getStatus()))
                        .count();
                BigDecimal metricValue = percent(failed, total);
                yield new AlertMetric(metricValue, total, windowStart, now, metricValue.compareTo(rule.getThresholdValue()) > 0);
            }
            case ERROR_CATEGORY_COUNT -> {
                long count = events.stream()
                        .filter(event -> StringUtils.hasText(rule.getErrorCategory())
                                ? rule.getErrorCategory().equals(event.getErrorCategory())
                                : ("FAILED".equals(event.getStatus()) || "BLOCKED".equals(event.getStatus())))
                        .count();
                BigDecimal metricValue = BigDecimal.valueOf(count);
                yield new AlertMetric(metricValue, count, windowStart, now, metricValue.compareTo(rule.getThresholdValue()) > 0);
            }
            case AVG_DURATION -> {
                long sampleCount = events.stream().filter(event -> event.getDurationMs() != null).count();
                BigDecimal metricValue = BigDecimal.valueOf(avgDuration(events));
                yield new AlertMetric(metricValue, sampleCount, windowStart, now, metricValue.compareTo(rule.getThresholdValue()) > 0);
            }
            case TOTAL_COST -> {
                BigDecimal metricValue = sumCost(events);
                yield new AlertMetric(metricValue, events.size(), windowStart, now, metricValue.compareTo(rule.getThresholdValue()) > 0);
            }
            case GUARDRAIL_BLOCK_COUNT -> {
                long count = events.stream()
                        .filter(event -> "GUARDRAIL".equals(event.getEventType()) || "BLOCKED".equals(event.getStatus()))
                        .count();
                BigDecimal metricValue = BigDecimal.valueOf(count);
                yield new AlertMetric(metricValue, count, windowStart, now, metricValue.compareTo(rule.getThresholdValue()) > 0);
            }
        };
    }

    private List<AgentObservationEvent> loadRuleWindowEvents(AgentObservationAlertRule rule, Date windowStart, Date windowEnd) {
        LambdaQueryWrapper<AgentObservationEvent> wrapper = new LambdaQueryWrapper<AgentObservationEvent>()
                .eq(AgentObservationEvent::getIsDeleted, NOT_DELETED)
                .ge(AgentObservationEvent::getCreateTime, windowStart)
                .le(AgentObservationEvent::getCreateTime, windowEnd);
        if (StringUtils.hasText(rule.getEventType())) {
            wrapper.eq(AgentObservationEvent::getEventType, rule.getEventType().trim());
        }
        if (StringUtils.hasText(rule.getModelCode())) {
            wrapper.eq(AgentObservationEvent::getModelCode, rule.getModelCode().trim());
        }
        if (StringUtils.hasText(rule.getToolName())) {
            wrapper.like(AgentObservationEvent::getToolName, rule.getToolName().trim());
        }
        return agentObservationEventMapper.selectList(wrapper);
    }

    private AgentObservationAlertRecord buildAlertRecord(AgentObservationAlertRule rule, AlertMetric metric, Date now) {
        AgentObservationAlertRecord record = new AgentObservationAlertRecord();
        record.setRuleId(rule.getId());
        record.setRuleName(rule.getRuleName());
        record.setRuleType(rule.getRuleType());
        record.setAlertLevel(firstText(rule.getAlertLevel(), "WARN"));
        record.setMetricValue(metric.metricValue());
        record.setThresholdValue(rule.getThresholdValue());
        record.setWindowStartTime(metric.windowStart());
        record.setWindowEndTime(metric.windowEnd());
        record.setAlertMessage(buildAlertMessage(rule, metric));
        record.setStatus(AgentObservationAlertStatus.OPEN.name());
        record.setIsDeleted(NOT_DELETED);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private String buildAlertMessage(AgentObservationAlertRule rule, AlertMetric metric) {
        return "规则[" + rule.getRuleName() + "]触发，当前值="
                + metric.metricValue().setScale(2, RoundingMode.HALF_UP)
                + "，阈值="
                + rule.getThresholdValue().setScale(2, RoundingMode.HALF_UP)
                + "，样本数="
                + metric.sampleCount();
    }

    private boolean inCooldown(AgentObservationAlertRule rule, Date now) {
        if (rule.getLastAlertTime() == null) {
            return false;
        }
        int cooldownMinutes = safePositive(rule.getCooldownMinutes(), DEFAULT_COOLDOWN_MINUTES);
        long cooldownMs = cooldownMinutes * 60_000L;
        return now.getTime() - rule.getLastAlertTime().getTime() < cooldownMs;
    }

    private void fillAlertRule(AgentObservationAlertRule rule, AgentObservationAlertRuleSaveDTO request) {
        String ruleName = requireText(request.getRuleName(), "规则名称不能为空");
        AgentObservationAlertRuleType ruleType = parseRuleType(request.getRuleType());
        if (request.getThresholdValue() == null) {
            throw new BizException("阈值不能为空");
        }
        rule.setRuleName(ruleName);
        rule.setRuleType(ruleType.name());
        rule.setEventType(trimToNull(request.getEventType()));
        rule.setErrorCategory(trimToNull(request.getErrorCategory()));
        rule.setModelCode(trimToNull(request.getModelCode()));
        rule.setToolName(trimToNull(request.getToolName()));
        rule.setThresholdValue(request.getThresholdValue());
        rule.setWindowMinutes(safePositive(request.getWindowMinutes(), DEFAULT_WINDOW_MINUTES));
        rule.setMinSampleCount(safePositive(request.getMinSampleCount(), DEFAULT_MIN_SAMPLE_COUNT));
        rule.setCooldownMinutes(safePositive(request.getCooldownMinutes(), DEFAULT_COOLDOWN_MINUTES));
        rule.setAlertLevel(firstText(request.getAlertLevel(), "WARN"));
        rule.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : AgentObservationConfigStatus.ACTIVE.name());
        rule.setRemark(trimToNull(request.getRemark()));
    }

    private AgentObservationAlertRule loadAlertRule(Long id) {
        AgentObservationAlertRule rule = agentObservationAlertRuleMapper.selectById(id);
        if (rule == null || Integer.valueOf(DELETED).equals(rule.getIsDeleted())) {
            throw new BizException("告警规则不存在");
        }
        return rule;
    }

    private void fillRetentionPolicy(AgentTraceRetentionPolicy policy, AgentTraceRetentionPolicySaveDTO request) {
        String policyName = requireText(request.getPolicyName(), "策略名称不能为空");
        String targetTable = assertAllowedTargetTable(request.getTargetTable());
        policy.setPolicyName(policyName);
        policy.setTargetTable(targetTable);
        policy.setRetentionDays(safePositive(request.getRetentionDays(), 30));
        policy.setBatchSize(safeBatchSize(request.getBatchSize()));
        policy.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : AgentObservationConfigStatus.ACTIVE.name());
        policy.setRemark(trimToNull(request.getRemark()));
    }

    private AgentTraceRetentionPolicy loadRetentionPolicy(Long id) {
        AgentTraceRetentionPolicy policy = agentTraceRetentionPolicyMapper.selectById(id);
        if (policy == null || Integer.valueOf(DELETED).equals(policy.getIsDeleted())) {
            throw new BizException("Trace 保留策略不存在");
        }
        return policy;
    }

    private AgentTraceRetentionPreviewVO previewPolicy(AgentTraceRetentionPolicy policy) {
        Date cutoffTime = retentionCutoff(policy);
        return AgentTraceRetentionPreviewVO.builder()
                .policyId(policy.getId())
                .targetTable(policy.getTargetTable())
                .retentionDays(policy.getRetentionDays())
                .cutoffTime(cutoffTime)
                .matchedCount(countRetentionMatched(policy.getTargetTable(), cutoffTime))
                .batchSize(safeBatchSize(policy.getBatchSize()))
                .build();
    }

    private Date retentionCutoff(AgentTraceRetentionPolicy policy) {
        return Date.from(LocalDateTime.now()
                .minusDays(safePositive(policy.getRetentionDays(), 30))
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }

    private Long countRetentionMatched(String targetTable, Date cutoffTime) {
        return switch (assertAllowedTargetTable(targetTable)) {
            case "agent_observation_event" -> agentObservationEventMapper.selectCount(new LambdaQueryWrapper<AgentObservationEvent>()
                    .eq(AgentObservationEvent::getIsDeleted, NOT_DELETED)
                    .lt(AgentObservationEvent::getCreateTime, cutoffTime));
            case "agent_trace_log" -> agentTraceLogMapper.selectCount(new LambdaQueryWrapper<AgentTraceLog>()
                    .eq(AgentTraceLog::getIsDeleted, NOT_DELETED)
                    .lt(AgentTraceLog::getCreateTime, cutoffTime));
            case "ai_model_call_log" -> aiModelCallLogMapper.selectCount(new LambdaQueryWrapper<AiModelCallLog>()
                    .eq(AiModelCallLog::getIsDeleted, NOT_DELETED)
                    .lt(AiModelCallLog::getCreateTime, cutoffTime));
            default -> 0L;
        };
    }

    private int executeLogicalDelete(String targetTable, Date cutoffTime, int batchSize) {
        Date now = new Date();
        return switch (assertAllowedTargetTable(targetTable)) {
            case "agent_observation_event" -> agentObservationEventMapper.update(null, new LambdaUpdateWrapper<AgentObservationEvent>()
                    .set(AgentObservationEvent::getIsDeleted, DELETED)
                    .set(AgentObservationEvent::getUpdateTime, now)
                    .eq(AgentObservationEvent::getIsDeleted, NOT_DELETED)
                    .lt(AgentObservationEvent::getCreateTime, cutoffTime)
                    .last("LIMIT " + batchSize));
            case "agent_trace_log" -> agentTraceLogMapper.update(null, new LambdaUpdateWrapper<AgentTraceLog>()
                    .set(AgentTraceLog::getIsDeleted, DELETED)
                    .set(AgentTraceLog::getUpdateTime, now)
                    .eq(AgentTraceLog::getIsDeleted, NOT_DELETED)
                    .lt(AgentTraceLog::getCreateTime, cutoffTime)
                    .last("LIMIT " + batchSize));
            case "ai_model_call_log" -> aiModelCallLogMapper.update(null, new LambdaUpdateWrapper<AiModelCallLog>()
                    .set(AiModelCallLog::getIsDeleted, DELETED)
                    .set(AiModelCallLog::getUpdateTime, now)
                    .eq(AiModelCallLog::getIsDeleted, NOT_DELETED)
                    .lt(AiModelCallLog::getCreateTime, cutoffTime)
                    .last("LIMIT " + batchSize));
            default -> 0;
        };
    }

    private String assertAllowedTargetTable(String targetTable) {
        if (!StringUtils.hasText(targetTable)) {
            throw new BizException("目标表不能为空");
        }
        String table = targetTable.trim();
        if (!List.of("agent_observation_event", "agent_trace_log", "ai_model_call_log").contains(table)) {
            throw new BizException("不支持的 Trace 保留目标表：" + targetTable);
        }
        return table;
    }

    private LambdaQueryWrapper<AgentObservationEvent> buildWrapper(AgentObservationEventQueryDTO query) {
        LambdaQueryWrapper<AgentObservationEvent> wrapper = new LambdaQueryWrapper<AgentObservationEvent>()
                .eq(AgentObservationEvent::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.like(AgentObservationEvent::getTraceId, query.getTraceId().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AgentObservationEvent::getUserId, query.getUserId());
        }
        if (query.getConversationId() != null) {
            wrapper.eq(AgentObservationEvent::getConversationId, query.getConversationId());
        }
        if (query.getPlanId() != null) {
            wrapper.eq(AgentObservationEvent::getPlanId, query.getPlanId());
        }
        if (query.getStepId() != null) {
            wrapper.eq(AgentObservationEvent::getStepId, query.getStepId());
        }
        if (StringUtils.hasText(query.getSceneCode())) {
            wrapper.eq(AgentObservationEvent::getSceneCode, query.getSceneCode().trim());
        }
        if (StringUtils.hasText(query.getIntentCode())) {
            wrapper.eq(AgentObservationEvent::getIntentCode, query.getIntentCode().trim());
        }
        if (StringUtils.hasText(query.getEventType())) {
            wrapper.eq(AgentObservationEvent::getEventType, query.getEventType().trim());
        }
        if (StringUtils.hasText(query.getEventName())) {
            wrapper.like(AgentObservationEvent::getEventName, query.getEventName().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AgentObservationEvent::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getErrorCategory())) {
            wrapper.eq(AgentObservationEvent::getErrorCategory, query.getErrorCategory().trim());
        }
        if (StringUtils.hasText(query.getModelCode())) {
            wrapper.eq(AgentObservationEvent::getModelCode, query.getModelCode().trim());
        }
        if (StringUtils.hasText(query.getToolName())) {
            wrapper.like(AgentObservationEvent::getToolName, query.getToolName().trim());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AgentObservationEvent::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AgentObservationEvent::getCreateTime, query.getEndTime().trim());
        }
        return wrapper;
    }

    private long countByStatus(List<AgentObservationEvent> events, String status) {
        return events.stream().filter(event -> status.equals(event.getStatus())).count();
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private Long avgDuration(List<AgentObservationEvent> events) {
        return Math.round(events.stream()
                .map(AgentObservationEvent::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
    }

    private Long maxDuration(List<AgentObservationEvent> events) {
        return events.stream()
                .map(AgentObservationEvent::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
    }

    private Long sumTokens(List<AgentObservationEvent> events) {
        return events.stream()
                .map(AgentObservationEvent::getTotalTokens)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private BigDecimal sumCost(List<AgentObservationEvent> events) {
        return events.stream()
                .map(AgentObservationEvent::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private Date lastTime(List<AgentObservationEvent> events) {
        return events.stream()
                .map(AgentObservationEvent::getCreateTime)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);
    }

    private AgentObservationAlertRuleType parseRuleType(String ruleType) {
        try {
            return AgentObservationAlertRuleType.valueOf(requireText(ruleType, "告警规则类型不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的告警规则类型：" + ruleType);
        }
    }

    private AgentObservationAlertStatus parseAlertStatus(String status) {
        try {
            return AgentObservationAlertStatus.valueOf(requireText(status, "告警状态不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的告警状态：" + status);
        }
    }

    private Date parseDateOrDefault(String value, Date defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            LocalDateTime time = LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
            return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int safeBatchSize(Integer value) {
        int batchSize = safePositive(value, DEFAULT_BATCH_SIZE);
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    @FunctionalInterface
    private interface StatKeyResolver {
        String resolve(AgentObservationEvent event);
    }

    private record AlertMetric(
            BigDecimal metricValue,
            long sampleCount,
            Date windowStart,
            Date windowEnd,
            boolean thresholdMatched
    ) {
    }
}
