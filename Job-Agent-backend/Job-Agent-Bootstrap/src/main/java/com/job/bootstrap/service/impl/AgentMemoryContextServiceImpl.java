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
 * Agent 长期记忆上下文服务实现，负责按 token 预算拼出本轮 Prompt 可用的记忆上下文。
 *
 * <p>核心职责：
 * 解决企业级长期记忆的核心矛盾“记忆库可以很大，但每次 Prompt 必须很小”。
 * MySQL 保存完整事实，profile 表保存压缩画像，本服务按字符预算组装 Prompt 上下文，
 * 并在记忆写入或归档后触发画像重建，保持画像与原始事实同步。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Memory 子模块（记忆上下文构建层）。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl.chat -> AgentMemoryContextService.buildContext（Prompt 上下文组装）
 * AgentMemoryCaptureServiceImpl.captureFromUserMessage -> rebuildProfile（画像重建）
 * AgentPlanExecutorServiceImpl 执行 Tool 后 -> AgentMemoryExtractionService -> rebuildProfile</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>buildContext 被 Agent 主链路在 Planner 前调用，产出 promptContext 注入到系统 Prompt；</li>
 *   <li>rebuildProfile 被 AgentMemoryCaptureService / AgentMemoryExtractionService 在写入或归档后调用；</li>
 *   <li>recallMemories 委托 AgentMemoryService.searchMemories 完成相关记忆召回，后续可替换为向量检索；</li>
 *   <li>profileSummary 当前存 MySQL，后续可缓存 Redis；searchMemories 内部召回算法后续可替换为向量库。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 这层解决企业级长期记忆的核心问题: “记忆库可以很大，但每次 Prompt 必须很小”。
 * 2. MySQL 保存完整事实，profile 表保存压缩画像，本服务负责按 token 预算拼出本轮可用上下文。
 * 3. 后续接入 Redis 时，可以缓存 profileSummary；接入向量库时，可以替换 searchMemories 内部召回算法。</p>
 *
 * 作者: hfj
 * 日期: 2026/6/23
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
     * 构造本轮 Prompt 可用的长期记忆上下文，覆盖画像读取、相关记忆召回与字符预算拼接。
     *
     * <p>核心处理流程：
     * 1. 校验 userId 有效性，无效时返回空上下文，避免后续查询无意义数据；
     * 2. 读取用户画像摘要（profileSummary），作为低成本、稳定的默认上下文；
     * 3. 调用 AgentMemoryService.searchMemories 按当前 query 召回少量相关记忆（默认 8 条），
     *    避免把所有历史事实塞进 Prompt 拖慢推理；
     * 4. 从画像开始拼接 Prompt 上下文，再逐条加入相关记忆，超过 PROMPT_CONTEXT_MAX_CHARS 即截断；
     * 5. 回填 profileSummary、命中记忆、promptContext、token 估算与 truncated 标识，便于 Trace 排查。</p>
     *
     * @param userId 当前求职用户 ID，用于定位画像与召回记忆
     * @param query  当前用户问题或检索词，用于相关记忆召回，可为空
     * @return 本轮长期记忆上下文 VO，包含 promptContext、命中记忆、画像摘要与 token 估算
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

    /**
     * 读取当前用户最新的有效画像摘要。
     *
     * <p>核心处理流程：
     * 1. 校验 userId 有效性，无效时直接返回 null；
     * 2. 按 updateTime 倒序查询 agent_user_memory_profile 中未删除的最新一条；
     * 3. 仅当 status=ACTIVE 时返回，避免已停用画像污染 Prompt 上下文。</p>
     *
     * @param userId 当前用户 ID
     * @return 有效画像 VO，不存在或已停用时返回 null
     */
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
     * 重建用户画像摘要，将分散的长期记忆压缩为稳定且可注入 Prompt 的画像文本。
     *
     * <p>核心处理流程：
     * 1. 校验 userId 有效性，缺失时抛 BizException 中断重建；
     * 2. 加载当前用户重要、有效、未删除的长期记忆，限制最多 100 条，避免后台重建扫描过多数据；
     * 3. 按固定 key 顺序（assistant_nickname、preferred_city、target_role 等）优先拼接稳定事实；
     * 4. 其余高重要性记忆作为“其他重要信息”补充，但仍受 PROFILE_MAX_CHARS 字符预算限制；
     * 5. 查询已有画像记录，存在则 profileVersion+1 更新，不存在则插入新记录；
     * 6. 写入或更新 agent_user_memory_profile，后续对话优先读取这份压缩摘要。</p>
     *
     * @param userId 当前用户 ID，用于定位长期记忆与画像记录
     * @return 重建后的画像 VO，包含压缩摘要、记忆条数、版本号与最后构建时间
     * @throws BizException userId 缺失或非正数时抛出，避免无效画像写入
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

    /**
     * 加载用于画像重建的长期记忆，按重要性 + 更新时间倒序取前 100 条。
     *
     * @param userId 当前用户 ID
     * @return 重要且未删除的有效长期记忆列表，最多 PROFILE_MEMORY_LIMIT 条
     */
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

    /**
     * 查询当前用户最新画像记录，用于判断本轮重建是 insert 还是 update。
     *
     * @param userId 当前用户 ID
     * @return 未删除的最新画像记录，不存在时返回 null
     */
    private AgentUserMemoryProfile findProfileForUpdate(Long userId) {
        return agentUserMemoryProfileMapper.selectOne(
                new LambdaQueryWrapper<AgentUserMemoryProfile>()
                        .eq(AgentUserMemoryProfile::getUserId, userId)
                        .eq(AgentUserMemoryProfile::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentUserMemoryProfile::getUpdateTime)
                        .last("LIMIT 1")
        );
    }

    /**
     * 将长期记忆列表压缩为画像摘要文本，固定 key 优先，其余高重要性记忆作为补充。
     *
     * <p>核心处理流程：
     * 1. 记忆为空时直接返回兜底文案“暂无稳定长期记忆。”；
     * 2. 按 memoryKey 去重保留首条，避免重复事实撑爆画像；
     * 3. 按 PROFILE_KEY_ORDER 固定顺序拼接稳定事实，保证画像可读性；
     * 4. 调用 buildExtraMemoryLines 追加最多 4 条其他重要信息作为补充；
     * 5. 最终结果受 PROFILE_MAX_CHARS 字符预算限制，超出部分截断。</p>
     *
     * @param memories 用于画像重建的长期记忆列表
     * @return 压缩后的画像摘要文本
     */
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

    /**
     * 构建画像中“其他重要信息”补充行，跳过已纳入固定 key 的事实，最多 4 条。
     *
     * @param memories 原始长期记忆列表
     * @param byKey    已去重的 memoryKey -> 记忆映射
     * @return 最多 4 条补充行文本，每行已截断到 80 字符
     */
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

    /**
     * 追加一行画像事实到 StringBuilder，value 为空时跳过，保证画像只包含有效信息。
     *
     * @param builder 画像文本构建器
     * @param label   画像字段中文标签
     * @param value   画像字段值
     */
    private void appendProfileLine(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append(label).append(": ").append(value.trim()).append('\n');
    }

    /**
     * 拼接本轮 Prompt 上下文文本，先画像后相关记忆，超过 PROMPT_CONTEXT_MAX_CHARS 即截断。
     *
     * <p>核心处理流程：
     * 1. 画像存在时先写入“【长期记忆画像】”段落，受 PROFILE_MAX_CHARS 限制；
     * 2. 相关记忆逐条编号写入“【本轮相关长期记忆】”段落，每条限制 120 字符；
     * 3. 单条追加会超过预算时停止追加并标记 truncated=true，避免 Prompt 过长拖慢推理；
     * 4. 最终结果再次受 PROMPT_CONTEXT_MAX_CHARS 兜底截断，并把 truncated 回写到 context。</p>
     *
     * @param profile         用户画像 VO，可为 null
     * @param relatedMemories 本轮召回的相关记忆列表，可为空
     * @param context         上下文 VO，用于回写 truncated 标识
     * @return 拼接后的 Prompt 上下文文本，可能为空字符串
     */
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

    /**
     * 粗略估算 Prompt 上下文 token 数，按 2 字符/token 计算，最少 1。
     *
     * @param text 待估算文本
     * @return token 估算值，文本为空时返回 0
     */
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
