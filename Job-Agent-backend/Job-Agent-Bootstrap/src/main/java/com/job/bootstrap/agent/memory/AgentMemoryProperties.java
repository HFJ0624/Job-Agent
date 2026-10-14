package com.job.bootstrap.agent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: Agent 长期记忆治理配置（任务9）。
 *
 * 说明:
 * 1. defaultTtlDays 控制记忆默认存活期，写入/更新时刷新过期时间。
 * 2. archiveCron 控制每日归档过期记忆的定时任务（默认每天凌晨 4 点）。
 */
@Data
@ConfigurationProperties(prefix = "job.agent.memory")
public class AgentMemoryProperties {

    /**
     * 长期记忆默认 TTL（天）。写入或更新时按 now + 该天数刷新 expire_time。
     */
    private int defaultTtlDays = 180;

    /**
     * 过期记忆归档任务 cron 表达式，默认每天凌晨 4 点。
     */
    private String archiveCron = "0 0 4 * * ?";
}
