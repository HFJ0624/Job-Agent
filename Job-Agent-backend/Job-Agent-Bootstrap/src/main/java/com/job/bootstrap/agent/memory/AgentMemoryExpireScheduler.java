package com.job.bootstrap.agent.memory;

import com.job.bootstrap.service.AgentMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 功能: 过期长期记忆归档调度器（任务9 TTL 治理）。
 *
 * 设计说明:
 * 1. 每日按 cron（默认凌晨 4 点，job.agent.memory.archive-cron 可配）把过期的 ACTIVE 记忆归档为 ARCHIVED，
 *    防止记忆无限增长、过期事实继续被召回。
 * 2. 归档失败不影响主流程，仅记录日志，下个周期自然重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentMemoryProperties.class)
public class AgentMemoryExpireScheduler {

    private final AgentMemoryService agentMemoryService;

    @Scheduled(cron = "${job.agent.memory.archive-cron:0 0 4 * * ?}")
    public void archiveExpiredMemories() {
        try {
            int archived = agentMemoryService.archiveExpiredMemories();
            if (archived > 0) {
                log.info("归档过期长期记忆 {} 条", archived);
            }
        } catch (Exception exception) {
            log.warn("归档过期长期记忆失败，error={}", exception.getMessage());
        }
    }
}
