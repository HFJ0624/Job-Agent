package com.job.bootstrap.agent.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: Agent 答案语义缓存配置（任务 G10）。
 *
 * 说明:
 * 1. enabled 一键开关，出问题可秒级退化为“无缓存”；
 * 2. similarityThreshold 是语义命中的余弦相似度门槛，0.92 表示“基本是同一个问题”；
 * 3. maxEntriesPerUser 限制单用户缓存条数（LRU 淘汰），防止内存无界增长；
 * 4. ttlMinutes 控制答案保鲜期，缓解“用户改了偏好/资料，缓存还答旧答案”。
 */
@Data
@ConfigurationProperties(prefix = "job.agent.cache")
public class AgentCacheProperties {

    /** 是否启用语义缓存。 */
    private boolean enabled = true;

    /** 语义命中阈值（余弦相似度，0-1）。 */
    private double similarityThreshold = 0.92;

    /** 单用户最大缓存条数（LRU）。 */
    private int maxEntriesPerUser = 200;

    /** 缓存存活分钟数。 */
    private long ttlMinutes = 30;
}
