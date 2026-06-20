package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentLongTermMemoryMapper;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.dto.agent.AgentMemoryQueryDTO;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryStatus;
import com.job.enums.AgentMemoryType;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆服务实现
 * 日期:2026/6/20
 */
@Service
@RequiredArgsConstructor
public class AgentMemoryServiceImpl implements AgentMemoryService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MEMORY_SCAN_LIMIT = 100;

    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.80");
    private static final BigDecimal DEFAULT_IMPORTANCE = new BigDecimal("0.50");

    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;

    /**
     * 保存或更新长期记忆。
     *
     * 方法步骤:
     * 1. 校验 userId、memoryType、memoryValue，避免写入无归属或无内容的记忆。
     * 2. 如果 memoryKey 有值，先查同一用户、同一类型、同一 key 的最新 ACTIVE 记忆。
     * 3. 查到旧记忆时更新它，没查到时插入新记忆。
     * 4. 统一补齐置信度、重要性、状态和时间字段。
     *
     * 这样设计的原因:
     * - preferred_city、target_role 这类事实应该被“覆盖更新”，否则用户改偏好后旧偏好会反复干扰。
     * - 没有 memoryKey 的事实可以保留多条，适合后续记录多次面试反馈或多次岗位判断。
     */
    @Override
    public AgentLongTermMemory saveOrUpdateMemory(
            Long userId,
            AgentMemoryType memoryType,
            String memoryKey,
            String memoryValue,
            String summary,
            String sourceType,
            Long sourceId,
            BigDecimal confidence,
            BigDecimal importance
    ) {
        if (userId == null || userId <= 0) {
            throw new BizException("长期记忆缺少有效用户ID");
        }
        if (memoryType == null) {
            throw new BizException("长期记忆缺少记忆类型");
        }
        if (!StringUtils.hasText(memoryValue)) {
            throw new BizException("长期记忆内容不能为空");
        }

        Date now = new Date();
        AgentLongTermMemory memory = findUpdatableMemory(userId, memoryType, memoryKey);
        boolean insert = memory == null;
        if (insert) {
            memory = new AgentLongTermMemory();
            memory.setUserId(userId);
            memory.setMemoryType(memoryType.name());
            memory.setMemoryKey(trimToNull(memoryKey));
            memory.setCreateTime(now);
            memory.setIsDeleted(NOT_DELETED);
        }

        memory.setMemoryValue(memoryValue.trim());
        memory.setSummary(resolveSummary(summary, memoryValue));
        memory.setSourceType(trimToNull(sourceType));
        memory.setSourceId(sourceId);
        memory.setConfidence(normalizeScore(confidence, DEFAULT_CONFIDENCE));
        memory.setImportance(normalizeScore(importance, DEFAULT_IMPORTANCE));
        memory.setStatus(AgentMemoryStatus.ACTIVE.name());
        memory.setUpdateTime(now);

        if (insert) {
            agentLongTermMemoryMapper.insert(memory);
        } else {
            agentLongTermMemoryMapper.updateById(memory);
        }
        return memory;
    }

    /**
     * 检索用户长期记忆。
     *
     * 方法步骤:
     * 1. 先取当前用户最近且重要的 ACTIVE 记忆，限定最多扫描 100 条，避免一次检索扫全表。
     * 2. 在 Java 内存中计算第一版相关性分数: 关键词命中 + 重要性 + 置信度。
     * 3. 按分数排序后截取 limit 条。
     * 4. 更新 lastUsedTime，方便后台知道哪些记忆真的被 Agent 使用过。
     *
     * 注意:
     * 第一版是结构化关键词检索，不是语义向量检索。
     * 后续接 pgvector 时，可以保留这个接口不变，只替换内部召回算法。
     */
    @Override
    public List<AgentMemoryVO> searchMemories(Long userId, String query, Integer limit) {
        if (userId == null || userId <= 0) {
            return List.of();
        }

        int actualLimit = resolveLimit(limit);
        List<AgentLongTermMemory> candidates = agentLongTermMemoryMapper.selectList(
                new LambdaQueryWrapper<AgentLongTermMemory>()
                        .eq(AgentLongTermMemory::getUserId, userId)
                        .eq(AgentLongTermMemory::getStatus, AgentMemoryStatus.ACTIVE.name())
                        .eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentLongTermMemory::getImportance)
                        .orderByDesc(AgentLongTermMemory::getUpdateTime)
                        .last("LIMIT " + MEMORY_SCAN_LIMIT)
        );
        if (CollectionUtils.isEmpty(candidates)) {
            return List.of();
        }

        List<String> tokens = tokenize(query);
        List<ScoredMemory> scoredMemories = new ArrayList<>();
        for (AgentLongTermMemory memory : candidates) {
            scoredMemories.add(new ScoredMemory(memory, score(memory, tokens)));
        }

        List<AgentLongTermMemory> selected = scoredMemories.stream()
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                        .thenComparing(memory -> safeDate(memory.memory().getUpdateTime()), Comparator.reverseOrder()))
                .limit(actualLimit)
                .map(ScoredMemory::memory)
                .toList();

        touchLastUsedTime(selected);
        return selected.stream().map(AgentMemoryVO::from).toList();
    }

    /**
     * 查询某个稳定记忆键的最新值。
     *
     * 典型用法:
     * - Executor 后续可用 preferred_city 补齐搜索城市。
     * - 话术生成可用 preferred_greeting_style 补齐用户沟通风格。
     */
    @Override
    public String findLatestMemoryValue(Long userId, AgentMemoryType memoryType, String memoryKey) {
        if (userId == null || userId <= 0 || memoryType == null || !StringUtils.hasText(memoryKey)) {
            return null;
        }

        AgentLongTermMemory memory = agentLongTermMemoryMapper.selectOne(
                new LambdaQueryWrapper<AgentLongTermMemory>()
                        .eq(AgentLongTermMemory::getUserId, userId)
                        .eq(AgentLongTermMemory::getMemoryType, memoryType.name())
                        .eq(AgentLongTermMemory::getMemoryKey, memoryKey.trim())
                        .eq(AgentLongTermMemory::getStatus, AgentMemoryStatus.ACTIVE.name())
                        .eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentLongTermMemory::getUpdateTime)
                        .last("LIMIT 1")
        );
        if (memory == null) {
            return null;
        }

        memory.setLastUsedTime(new Date());
        memory.setUpdateTime(new Date());
        agentLongTermMemoryMapper.updateById(memory);
        return memory.getMemoryValue();
    }

    /**
     * 后台分页查询长期记忆。
     */
    @Override
    public IPage<AgentMemoryVO> pageMemories(AgentMemoryQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() <= 0 ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10L : query.getPageSize();
        if (pageSize > 100) {
            pageSize = 100;
        }

        Page<AgentLongTermMemory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AgentLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED);

        if (query.getUserId() != null) {
            wrapper.eq(AgentLongTermMemory::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getMemoryType())) {
            wrapper.eq(AgentLongTermMemory::getMemoryType, query.getMemoryType().trim());
        }
        if (StringUtils.hasText(query.getMemoryKey())) {
            wrapper.like(AgentLongTermMemory::getMemoryKey, query.getMemoryKey().trim());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(AgentLongTermMemory::getSourceType, query.getSourceType().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AgentLongTermMemory::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item
                    .like(AgentLongTermMemory::getMemoryKey, keyword)
                    .or()
                    .like(AgentLongTermMemory::getSummary, keyword)
                    .or()
                    .like(AgentLongTermMemory::getMemoryValue, keyword));
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AgentLongTermMemory::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AgentLongTermMemory::getCreateTime, query.getEndTime().trim());
        }

        wrapper.orderByDesc(AgentLongTermMemory::getUpdateTime);
        return agentLongTermMemoryMapper.selectPage(page, wrapper).convert(AgentMemoryVO::from);
    }

    /**
     * 查询长期记忆详情。
     */
    @Override
    public AgentMemoryVO getDetail(Long id) {
        AgentLongTermMemory memory = agentLongTermMemoryMapper.selectById(id);
        if (memory == null || Integer.valueOf(DELETED).equals(memory.getIsDeleted())) {
            throw new BizException("Agent 长期记忆不存在");
        }
        return AgentMemoryVO.from(memory);
    }

    private AgentLongTermMemory findUpdatableMemory(Long userId, AgentMemoryType memoryType, String memoryKey) {
        if (!StringUtils.hasText(memoryKey)) {
            return null;
        }

        return agentLongTermMemoryMapper.selectOne(
                new LambdaQueryWrapper<AgentLongTermMemory>()
                        .eq(AgentLongTermMemory::getUserId, userId)
                        .eq(AgentLongTermMemory::getMemoryType, memoryType.name())
                        .eq(AgentLongTermMemory::getMemoryKey, memoryKey.trim())
                        .eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentLongTermMemory::getUpdateTime)
                        .last("LIMIT 1")
        );
    }

    private String resolveSummary(String summary, String memoryValue) {
        String text = StringUtils.hasText(summary) ? summary.trim() : memoryValue.trim();
        return text.length() <= 512 ? text : text.substring(0, 512);
    }

    private BigDecimal normalizeScore(BigDecimal value, BigDecimal defaultValue) {
        BigDecimal score = value == null ? defaultValue : value;
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            score = BigDecimal.ONE;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    private List<String> tokenize(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        String normalized = query.trim().toLowerCase();
        if (normalized.length() <= 40) {
            tokens.add(normalized);
        }

        String[] parts = normalized.split("[\\s,，。.!！?？、;；:：/\\\\|]+");
        for (String part : parts) {
            if (part.length() >= 2) {
                tokens.add(part);
            }
        }
        return new ArrayList<>(tokens);
    }

    private double score(AgentLongTermMemory memory, List<String> tokens) {
        double score = decimalScore(memory.getImportance()) * 2 + decimalScore(memory.getConfidence());
        if (CollectionUtils.isEmpty(tokens)) {
            return score;
        }

        String key = lower(memory.getMemoryKey());
        String summary = lower(memory.getSummary());
        String value = lower(memory.getMemoryValue());
        String all = key + " " + summary + " " + value;

        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String normalizedToken = token.toLowerCase();
            if (key.contains(normalizedToken)) {
                score += 4;
            }
            if (summary.contains(normalizedToken)) {
                score += 3;
            }
            if (value.contains(normalizedToken)) {
                score += 2;
            }
            if (!key.contains(normalizedToken)
                    && !summary.contains(normalizedToken)
                    && !value.contains(normalizedToken)
                    && all.contains(normalizedToken)) {
                score += 1;
            }
        }
        return score;
    }

    private void touchLastUsedTime(List<AgentLongTermMemory> memories) {
        if (CollectionUtils.isEmpty(memories)) {
            return;
        }

        Date now = new Date();
        for (AgentLongTermMemory memory : memories) {
            memory.setLastUsedTime(now);
            memory.setUpdateTime(now);
            agentLongTermMemoryMapper.updateById(memory);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private double decimalScore(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private long safeDate(Date date) {
        return date == null ? 0L : date.getTime();
    }

    private record ScoredMemory(AgentLongTermMemory memory, double score) {
    }
}
