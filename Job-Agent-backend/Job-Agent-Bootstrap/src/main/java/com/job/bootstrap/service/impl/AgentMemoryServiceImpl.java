package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentLongTermMemoryMapper;
import com.job.bootstrap.mapper.AgentMemoryHistoryMapper;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.dto.agent.AgentMemoryQueryDTO;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.entity.agent.AgentMemoryHistory;
import com.job.common.vo.agent.AgentMemoryHistoryVO;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryStatus;
import com.job.enums.AgentMemoryType;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.Set;

/**
 * Agent 长期记忆服务实现，是 Memory 模块的主表读写入口。
 *
 * <p>核心职责：
 * 1. 提供长期记忆的保存或更新（按 userId + memoryType + memoryKey 唯一）。
 * 2. 提供基于关键词的相关性检索（第一版用 Java 内存打分，后续可替换为向量召回）。
 * 3. 记录记忆变更历史（CREATE/UPDATE/STATUS_CHANGE），并标记同 key 覆盖冲突。
 * 4. 支持后台人工更新状态、按 key 归档，保证记忆可审计、可回滚。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Memory Service 层。</p>
 *
 * <p>主要调用链：
 * AgentMemoryCaptureServiceImpl (用户消息捕获) -> saveOrUpdateMemory
 * AgentMemoryExtractionServiceImpl (工具结果沉淀) -> saveOrUpdateMemory
 * AgentMemoryContextServiceImpl (上下文召回) -> searchMemories
 * AgentChatServiceImpl (画像重建触发) -> archiveActiveMemoriesByKeys</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>AgentMemoryCaptureService 负责从用户消息捕获记忆，本服务负责落库；</li>
 *   <li>AgentMemoryExtractionService 负责从工具结果提取记忆，本服务负责落库；</li>
 *   <li>AgentMemoryContextService 调用 searchMemories 召回相关记忆并控制 Prompt token；</li>
 *   <li>AgentMemoryHistoryMapper 记录变更历史，支持后台审计与冲突排查。</li>
 * </ul></p>
 *
 * <p>Memory 读写逻辑说明：
 * 写入：按 key 查找已有记忆，存在则覆盖并写历史，不存在则新增；
 * 召回：先按重要性 + 时间取候选 100 条，再在 Java 内按关键词打分排序，取 limit 条；
 * 归档：按 key 把 ACTIVE 记忆改为 ARCHIVED，写 STATUS_CHANGE 历史。</p>
 *
 * 作者: hfj
 * 日期: 2026/6/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryServiceImpl implements AgentMemoryService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int CONFLICT_NO = 0;
    private static final int CONFLICT_YES = 1;

    private static final String CHANGE_CREATE = "CREATE";
    private static final String CHANGE_UPDATE = "UPDATE";
    private static final String CHANGE_STATUS = "STATUS_CHANGE";
    private static final String OPERATOR_SYSTEM = "SYSTEM";
    private static final String OPERATOR_ADMIN = "ADMIN";

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 20;
    private static final int MEMORY_SCAN_LIMIT = 100;

    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.80");
    private static final BigDecimal DEFAULT_IMPORTANCE = new BigDecimal("0.50");

    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private final AgentMemoryHistoryMapper agentMemoryHistoryMapper;

    /**
     * 保存或更新长期记忆。
     *
     * 方法步骤:
     * 1. 校验用户、记忆类型、记忆正文，避免写入无效事实。
     * 2. 如果 memoryKey 存在，查找同用户、同类型、同 key 的当前记忆。
     * 3. 当前记忆不存在则新增，存在则覆盖当前值。
     * 4. 主表写入成功后记录版本历史。
     * 5. 如果同 key 覆盖时新旧值不同，历史记录会标记 conflictDetected=1。
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
        AgentLongTermMemory oldSnapshot = copyMemory(memory);
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

        recordMemoryHistory(
                oldSnapshot,
                memory,
                insert ? CHANGE_CREATE : CHANGE_UPDATE,
                sourceType,
                sourceId,
                OPERATOR_SYSTEM
        );
        return memory;
    }

    /**
     * 检索用户长期记忆。
     *
     * 方法步骤:
     * 1. 读取当前用户最近且重要的 ACTIVE 记忆，限制扫描数量。
     * 2. 在 Java 内存里按关键词、重要性、置信度计算第一版相关性分数。
     * 3. 按分数排序后截取 limit 条。
     * 4. 更新 lastUsedTime，方便后台知道哪些记忆被召回过。
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

    @Override
    public AgentMemoryVO getDetail(Long id) {
        AgentLongTermMemory memory = agentLongTermMemoryMapper.selectById(id);
        if (memory == null || Integer.valueOf(DELETED).equals(memory.getIsDeleted())) {
            throw new BizException("Agent 长期记忆不存在");
        }
        return AgentMemoryVO.from(memory);
    }

    @Override
    public List<AgentMemoryHistoryVO> listHistory(Long memoryId) {
        if (memoryId == null || memoryId <= 0) {
            throw new BizException("长期记忆ID不能为空");
        }

        List<AgentMemoryHistory> histories = agentMemoryHistoryMapper.selectList(
                new LambdaQueryWrapper<AgentMemoryHistory>()
                        .eq(AgentMemoryHistory::getMemoryId, memoryId)
                        .eq(AgentMemoryHistory::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentMemoryHistory::getCreateTime)
                        .orderByDesc(AgentMemoryHistory::getId)
        );
        return histories.stream().map(AgentMemoryHistoryVO::from).toList();
    }

    /**
     * 后台人工更新长期记忆状态。
     *
     * 方法步骤:
     * 1. 查询旧记忆并复制快照。
     * 2. 更新主表状态。
     * 3. 写入 STATUS_CHANGE 历史记录，方便后台审计谁把记忆禁用或恢复。
     */
    @Override
    public AgentMemoryVO updateStatus(Long id, String status) {
        if (id == null || id <= 0) {
            throw new BizException("长期记忆ID不能为空");
        }

        AgentLongTermMemory memory = agentLongTermMemoryMapper.selectById(id);
        if (memory == null || Integer.valueOf(DELETED).equals(memory.getIsDeleted())) {
            throw new BizException("Agent 长期记忆不存在");
        }

        AgentLongTermMemory oldSnapshot = copyMemory(memory);
        AgentMemoryStatus targetStatus = parseMemoryStatus(status);
        memory.setStatus(targetStatus.name());
        memory.setUpdateTime(new Date());
        agentLongTermMemoryMapper.updateById(memory);

        recordMemoryHistory(oldSnapshot, memory, CHANGE_STATUS, memory.getSourceType(), memory.getSourceId(), OPERATOR_ADMIN);
        return AgentMemoryVO.from(memory);
    }

    /**
     * 按 key 归档当前用户的 ACTIVE 记忆。
     */
    @Override
    public int archiveActiveMemoriesByKeys(Long userId, List<String> memoryKeys) {
        if (userId == null || userId <= 0 || CollectionUtils.isEmpty(memoryKeys)) {
            return 0;
        }

        List<String> normalizedKeys = memoryKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (CollectionUtils.isEmpty(normalizedKeys)) {
            return 0;
        }

        List<AgentLongTermMemory> memories = agentLongTermMemoryMapper.selectList(
                new LambdaQueryWrapper<AgentLongTermMemory>()
                        .eq(AgentLongTermMemory::getUserId, userId)
                        .in(AgentLongTermMemory::getMemoryKey, normalizedKeys)
                        .eq(AgentLongTermMemory::getStatus, AgentMemoryStatus.ACTIVE.name())
                        .eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED)
        );
        if (CollectionUtils.isEmpty(memories)) {
            return 0;
        }

        Date now = new Date();
        for (AgentLongTermMemory memory : memories) {
            AgentLongTermMemory oldSnapshot = copyMemory(memory);
            memory.setStatus(AgentMemoryStatus.ARCHIVED.name());
            memory.setUpdateTime(now);
            agentLongTermMemoryMapper.updateById(memory);
            recordMemoryHistory(oldSnapshot, memory, CHANGE_STATUS, memory.getSourceType(), memory.getSourceId(), OPERATOR_SYSTEM);
        }
        return memories.size();
    }

    private void recordMemoryHistory(
            AgentLongTermMemory oldMemory,
            AgentLongTermMemory newMemory,
            String changeType,
            String sourceType,
            Long sourceId,
            String operatorType
    ) {
        if (newMemory == null || newMemory.getId() == null) {
            return;
        }

        try {
            /*
             * 历史记录是审计能力，不应该影响主记忆写入。
             * 如果你还没建 agent_memory_history 表，这里只记录日志，Agent 主流程继续运行。
             */
            Date now = new Date();
            AgentMemoryHistory history = new AgentMemoryHistory();
            history.setMemoryId(newMemory.getId());
            history.setUserId(newMemory.getUserId());
            history.setMemoryType(newMemory.getMemoryType());
            history.setMemoryKey(newMemory.getMemoryKey());
            history.setChangeType(changeType);
            history.setOldMemoryValue(oldMemory == null ? null : oldMemory.getMemoryValue());
            history.setNewMemoryValue(newMemory.getMemoryValue());
            history.setOldSummary(oldMemory == null ? null : oldMemory.getSummary());
            history.setNewSummary(newMemory.getSummary());
            history.setOldStatus(oldMemory == null ? null : oldMemory.getStatus());
            history.setNewStatus(newMemory.getStatus());
            history.setConflictDetected(detectConflict(oldMemory, newMemory, changeType));
            history.setConflictReason(resolveConflictReason(history));
            history.setSourceType(trimToNull(sourceType));
            history.setSourceId(sourceId);
            history.setOperatorType(operatorType);
            history.setIsDeleted(NOT_DELETED);
            history.setCreateTime(now);
            history.setUpdateTime(now);
            agentMemoryHistoryMapper.insert(history);
        } catch (Exception exception) {
            log.warn(
                    "Agent 记忆历史写入失败，memoryId={}, changeType={}, error={}",
                    newMemory.getId(),
                    changeType,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Integer detectConflict(AgentLongTermMemory oldMemory, AgentLongTermMemory newMemory, String changeType) {
        /*
         * 第二版先做确定性冲突:
         * 同一条稳定 key 记忆被覆盖，并且新旧正文不同，就标记冲突。
         * 例如 preferred_city 从“北京”变成“上海”。
         */
        if (oldMemory == null || newMemory == null || !CHANGE_UPDATE.equals(changeType)) {
            return CONFLICT_NO;
        }
        if (!StringUtils.hasText(newMemory.getMemoryKey())) {
            return CONFLICT_NO;
        }
        String oldValue = normalizeForCompare(oldMemory.getMemoryValue());
        String newValue = normalizeForCompare(newMemory.getMemoryValue());
        return oldValue.equals(newValue) ? CONFLICT_NO : CONFLICT_YES;
    }

    private String resolveConflictReason(AgentMemoryHistory history) {
        if (history == null || !Integer.valueOf(CONFLICT_YES).equals(history.getConflictDetected())) {
            return null;
        }
        return "同一 memoryKey 的长期记忆被新值覆盖，请确认这是用户偏好变化还是错误提取。";
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

    private AgentMemoryStatus parseMemoryStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BizException("记忆状态不能为空");
        }

        try {
            return AgentMemoryStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new BizException("不支持的记忆状态: " + status);
        }
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
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= 40) {
            tokens.add(normalized);
        }

        String[] parts = normalized.split("[\\s,，。!！?？;；:：、|\\\\]+");
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
            String normalizedToken = token.toLowerCase(Locale.ROOT);
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

    private AgentLongTermMemory copyMemory(AgentLongTermMemory source) {
        if (source == null) {
            return null;
        }
        AgentLongTermMemory copy = new AgentLongTermMemory();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setMemoryType(source.getMemoryType());
        copy.setMemoryKey(source.getMemoryKey());
        copy.setMemoryValue(source.getMemoryValue());
        copy.setSummary(source.getSummary());
        copy.setSourceType(source.getSourceType());
        copy.setSourceId(source.getSourceId());
        copy.setConfidence(source.getConfidence());
        copy.setImportance(source.getImportance());
        copy.setStatus(source.getStatus());
        copy.setLastUsedTime(source.getLastUsedTime());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setIsDeleted(source.getIsDeleted());
        return copy;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeForCompare(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
