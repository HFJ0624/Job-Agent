package com.job.bootstrap.agent.cache;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 功能: 答案语义缓存编排（任务 G10）。
 *
 * 设计说明:
 * 1. 挂点价值：命中即短路整条 Agent 链路（规划/执行/总结全部跳过），
 *    重复或改写提问的响应从“数秒 + 多次模型调用”变成“一次 embedding + 内存比对”；
 * 2. 缓存键用 G8 改写后的自包含查询——“那第二个呢”与完整表述能互相命中；
 * 3. 用户隔离：所有操作带 userId，缓存只在同用户内命中；
 * 4. 防御性降级：embedding 调用失败（未配置/网络）时 lookup 返回 empty、
 *    remember 静默跳过——缓存是加速件，任何故障都不能拖慢或阻断主流程；
 * 5. 可观测：HIT/MISS/WRITE 三类 CACHE 事件，后台可统计命中率与省钱量。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentCacheProperties.class)
public class AgentAnswerCacheService {

    private final AgentCacheProperties properties;
    private final CacheStore cacheStore;
    private final RagEmbeddingService ragEmbeddingService;
    private final AgentObservationService agentObservationService;

    /**
     * 查询语义相似的历史答案。
     *
     * @param userId        用户 ID
     * @param query         自包含查询（G8 改写后的消息）
     * @return 命中答案；未命中/降级返回 empty
     */
    public Optional<String> lookup(Long userId, String query) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(query)) {
            return Optional.empty();
        }

        float[] embedding = embed(query);
        if (embedding == null) {
            return Optional.empty();
        }

        Optional<CacheStore.CacheEntry> hit =
                cacheStore.lookup(userId, embedding, properties.getSimilarityThreshold());
        if (hit.isPresent()) {
            recordEvent(userId, "CacheHit", hit.get().question());
            return Optional.ofNullable(hit.get().answer());
        }
        recordEvent(userId, "CacheMiss", query.trim());
        return Optional.empty();
    }

    /**
     * 把成功答案写回缓存（调用方保证只传可缓存的答案：非确认中/非失败/非拦截）。
     *
     * @param userId 用户 ID
     * @param query  自包含查询
     * @param answer 最终答案
     */
    public void remember(Long userId, String query, String answer) {
        if (!properties.isEnabled() || userId == null
                || !StringUtils.hasText(query) || !StringUtils.hasText(answer)) {
            return;
        }

        try {
            float[] embedding = ragEmbeddingService.embed(query.trim());
            long expireAt = System.currentTimeMillis()
                    + Math.max(1, properties.getTtlMinutes()) * 60_000L;
            cacheStore.put(userId, new CacheStore.CacheEntry(query.trim(), embedding, answer.trim(), expireAt));
            recordEvent(userId, "CacheWrite", query.trim());
        } catch (Exception exception) {
            // 缓存写失败只降级，不影响主流程
            log.warn("答案缓存写入失败，userId={}, error={}", userId, exception.getMessage());
        }
    }

    private float[] embed(String query) {
        try {
            return ragEmbeddingService.embed(query.trim());
        } catch (Exception exception) {
            // embedding 不可用（未配置/网络）：缓存整体退化为不可用
            log.warn("语义缓存 embedding 失败，本次降级为无缓存，error={}", exception.getMessage());
            return null;
        }
    }

    private void recordEvent(Long userId, String eventName, String question) {
        Map<String, Object> requestSnapshot = new LinkedHashMap<>();
        requestSnapshot.put("question", question);

        try {
            agentObservationService.recordEvent(AgentObservationRecord.builder()
                    .userId(userId)
                    .eventType(AgentObservationEventType.CACHE)
                    .eventName(eventName)
                    .status(AgentObservationStatus.SUCCESS)
                    .requestSnapshot(requestSnapshot)
                    .build());
        } catch (Exception exception) {
            log.debug("缓存观测事件记录失败，userId={}, error={}", userId, exception.getMessage());
        }
    }
}
