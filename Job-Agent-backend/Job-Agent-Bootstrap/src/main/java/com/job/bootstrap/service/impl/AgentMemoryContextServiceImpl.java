package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentLongTermMemoryMapper;
import com.job.bootstrap.mapper.AgentUserMemoryProfileMapper;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.entity.agent.AgentUserMemoryProfile;
import com.job.common.vo.agent.AgentMemoryContextVO;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.common.vo.agent.AgentUserMemoryProfileVO;
import com.job.enums.AgentMemoryStatus;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆上下文服务实现
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 这层解决企业级长期记忆的核心问题: “记忆库可以很大，但每次 Prompt 必须很小”。
 * 2. MySQL 保存完整事实，profile 表保存压缩画像，本服务负责按 token 预算拼出本轮可用上下文。
 * 3. 后续接入 Redis 时，可以缓存 profileSummary；接入向量库时，可以替换 searchMemories 内部召回算法。
 */
@Service
@RequiredArgsConstructor
public class AgentMemoryContextServiceImpl implements AgentMemoryContextService {

    private static final int NOT_DELETED = 0;
    private static final int PROFILE_MEMORY_LIMIT = 100;
    private static final int RELATED_MEMORY_LIMIT = 8;
    private static final int PROFILE_MAX_CHARS = 700;
    private static final int PROMPT_CONTEXT_MAX_CHARS = 1600;
    private static final String ACTIVE = "ACTIVE";

    private static final List<String> PROFILE_KEY_ORDER = List.of(
            "assistant_nickname",
            "user_name",
            "preferred_city",
            "target_role",
            "target_position",
            "min_salary",
            "answer_style",
            "preferred_greeting_style",
            "excluded_outsourcing",
            "excluded_996",
            "excluded_big_small_week",
            "recent_search_goal"
    );

    private static final Map<String, String> PROFILE_LABELS = Map.ofEntries(
            Map.entry("assistant_nickname", "助手称呼"),
            Map.entry("user_name", "用户称呼"),
            Map.entry("preferred_city", "偏好城市"),
            Map.entry("target_role", "目标岗位"),
            Map.entry("target_position", "目标职位"),
            Map.entry("min_salary", "最低薪资"),
            Map.entry("answer_style", "回答风格"),
            Map.entry("preferred_greeting_style", "HR话术风格"),
            Map.entry("excluded_outsourcing", "排除外包"),
            Map.entry("excluded_996", "排除996"),
            Map.entry("excluded_big_small_week", "排除大小周"),
            Map.entry("recent_search_goal", "近期搜索方向")
    );

    private final AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private final AgentUserMemoryProfileMapper agentUserMemoryProfileMapper;
    private final AgentMemoryService agentMemoryService;

    /**
     * 构造本轮 Prompt 可用的长期记忆上下文。
     *
     * 方法步骤:
     * 1. 读取用户画像摘要，作为低成本、稳定的默认上下文。
     * 2. 按当前问题召回少量相关记忆，避免把所有历史事实塞进 Prompt。
     * 3. 从画像开始拼接，再逐条加入相关记忆，超过字符预算就停止。
     * 4. 返回 promptContext、命中记忆和粗略 token 估算，方便 Trace 排查。
     *
     * @param userId 当前用户 ID
     * @param query 当前问题或检索词
     * @return 本轮长期记忆上下文
     */
    @Override
    public AgentMemoryContextVO buildContext(Long userId, String query) {
        AgentMemoryContextVO context = new AgentMemoryContextVO();
        if (userId == null || userId <= 0) {
            context.setPromptContext("");
            context.setEstimatedTokens(0);
            context.setTruncated(false);
            return context;
        }

        AgentUserMemoryProfileVO profile = getProfile(userId);
        List<AgentMemoryVO> relatedMemories = agentMemoryService.searchMemories(userId, query, RELATED_MEMORY_LIMIT);
        String promptContext = buildPromptContext(profile, relatedMemories, context);

        context.setProfileSummary(profile == null ? null : profile.getProfileSummary());
        context.setMemories(relatedMemories);
        context.setPromptContext(promptContext);
        context.setEstimatedTokens(estimateTokens(promptContext));
        return context;
    }

    @Override
    public AgentUserMemoryProfileVO getProfile(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }

        AgentUserMemoryProfile profile = agentUserMemoryProfileMapper.selectOne(
                new LambdaQueryWrapper<AgentUserMemoryProfile>()
                        .eq(AgentUserMemoryProfile::getUserId, userId)
                        .eq(AgentUserMemoryProfile::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentUserMemoryProfile::getUpdateTime)
                        .last("LIMIT 1")
        );
        if (profile == null || !ACTIVE.equals(profile.getStatus())) {
            return null;
        }
        return AgentUserMemoryProfileVO.from(profile);
    }

    /**
     * 重建用户画像摘要。
     *
     * 方法步骤:
     * 1. 读取当前用户最近、重要、有效的长期记忆，限制最多 100 条，避免后台重建时扫描过多数据。
     * 2. 优先选择固定 key 的稳定事实，例如 assistant_nickname、preferred_city、target_role。
     * 3. 其余高重要性记忆作为“其他重要信息”补充，但仍受画像字符预算限制。
     * 4. 写入或更新 agent_user_memory_profile，后续对话优先读取这份压缩摘要。
     *
     * @param userId 用户 ID
     * @return 重建后的画像摘要
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentUserMemoryProfileVO rebuildProfile(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("重建长期记忆画像缺少有效用户ID");
        }

        List<AgentLongTermMemory> memories = loadProfileMemories(userId);
        String profileSummary = buildProfileSummary(memories);
        AgentUserMemoryProfile profile = findProfileForUpdate(userId);
        Date now = new Date();
        boolean insert = profile == null;

        if (insert) {
            profile = new AgentUserMemoryProfile();
            profile.setUserId(userId);
            profile.setProfileVersion(1);
            profile.setCreateTime(now);
            profile.setIsDeleted(NOT_DELETED);
        } else {
            profile.setProfileVersion(profile.getProfileVersion() == null ? 1 : profile.getProfileVersion() + 1);
        }

        profile.setProfileSummary(profileSummary);
        profile.setMemoryCount(memories.size());
        profile.setLastBuildTime(now);
        profile.setStatus(ACTIVE);
        profile.setUpdateTime(now);

        if (insert) {
            agentUserMemoryProfileMapper.insert(profile);
        } else {
            agentUserMemoryProfileMapper.updateById(profile);
        }
        return AgentUserMemoryProfileVO.from(profile);
    }

    private List<AgentLongTermMemory> loadProfileMemories(Long userId) {
        return agentLongTermMemoryMapper.selectList(
                new LambdaQueryWrapper<AgentLongTermMemory>()
                        .eq(AgentLongTermMemory::getUserId, userId)
                        .eq(AgentLongTermMemory::getStatus, AgentMemoryStatus.ACTIVE.name())
                        .eq(AgentLongTermMemory::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentLongTermMemory::getImportance)
                        .orderByDesc(AgentLongTermMemory::getUpdateTime)
                        .last("LIMIT " + PROFILE_MEMORY_LIMIT)
        );
    }

    private AgentUserMemoryProfile findProfileForUpdate(Long userId) {
        return agentUserMemoryProfileMapper.selectOne(
                new LambdaQueryWrapper<AgentUserMemoryProfile>()
                        .eq(AgentUserMemoryProfile::getUserId, userId)
                        .eq(AgentUserMemoryProfile::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentUserMemoryProfile::getUpdateTime)
                        .last("LIMIT 1")
        );
    }

    private String buildProfileSummary(List<AgentLongTermMemory> memories) {
        if (CollectionUtils.isEmpty(memories)) {
            return "暂无稳定长期记忆。";
        }

        Map<String, AgentLongTermMemory> byKey = new LinkedHashMap<>();
        for (AgentLongTermMemory memory : memories) {
            if (StringUtils.hasText(memory.getMemoryKey())) {
                byKey.putIfAbsent(memory.getMemoryKey(), memory);
            }
        }

        StringBuilder builder = new StringBuilder();
        for (String key : PROFILE_KEY_ORDER) {
            AgentLongTermMemory memory = byKey.get(key);
            if (memory != null) {
                appendProfileLine(builder, PROFILE_LABELS.getOrDefault(key, key), memory.getMemoryValue());
            }
        }

        List<String> extras = buildExtraMemoryLines(memories, byKey);
        if (!extras.isEmpty()) {
            builder.append("其他重要信息: ");
            builder.append(String.join("；", extras));
            builder.append('\n');
        }

        String summary = builder.toString().trim();
        return truncate(StringUtils.hasText(summary) ? summary : "暂无稳定长期记忆。", PROFILE_MAX_CHARS);
    }

    private List<String> buildExtraMemoryLines(List<AgentLongTermMemory> memories, Map<String, AgentLongTermMemory> byKey) {
        List<String> extras = new ArrayList<>();
        for (AgentLongTermMemory memory : memories) {
            if (memory.getMemoryKey() != null && byKey.containsKey(memory.getMemoryKey())
                    && PROFILE_KEY_ORDER.contains(memory.getMemoryKey())) {
                continue;
            }

            String summary = StringUtils.hasText(memory.getSummary()) ? memory.getSummary() : memory.getMemoryValue();
            if (StringUtils.hasText(summary)) {
                extras.add(truncate(summary, 80));
            }
            if (extras.size() >= 4) {
                break;
            }
        }
        return extras;
    }

    private void appendProfileLine(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append(label).append(": ").append(value.trim()).append('\n');
    }

    private String buildPromptContext(
            AgentUserMemoryProfileVO profile,
            List<AgentMemoryVO> relatedMemories,
            AgentMemoryContextVO context
    ) {
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;

        if (profile != null && StringUtils.hasText(profile.getProfileSummary())) {
            builder.append("【长期记忆画像】\n")
                    .append(truncate(profile.getProfileSummary(), PROFILE_MAX_CHARS))
                    .append("\n\n");
        }

        if (!CollectionUtils.isEmpty(relatedMemories)) {
            builder.append("【本轮相关长期记忆】\n");
            int index = 1;
            for (AgentMemoryVO memory : relatedMemories) {
                String line = index + ". "
                        + nullToDash(memory.getMemoryType())
                        + "/"
                        + nullToDash(memory.getMemoryKey())
                        + ": "
                        + truncate(firstText(memory.getSummary(), memory.getMemoryValue()), 120)
                        + "\n";
                if (builder.length() + line.length() > PROMPT_CONTEXT_MAX_CHARS) {
                    truncated = true;
                    break;
                }
                builder.append(line);
                index++;
            }
        }

        String text = truncate(builder.toString().trim(), PROMPT_CONTEXT_MAX_CHARS);
        context.setTruncated(truncated || builder.length() > PROMPT_CONTEXT_MAX_CHARS);
        return text;
    }

    private Integer estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
