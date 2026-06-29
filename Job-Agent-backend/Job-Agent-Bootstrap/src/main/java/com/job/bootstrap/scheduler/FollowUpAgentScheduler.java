package com.job.bootstrap.scheduler;

import com.job.bootstrap.service.AdminFollowUpAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 求职跟进 Agent 定时扫描任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpAgentScheduler {

    private final AdminFollowUpAgentService adminFollowUpAgentService;

    /**
     * 每小时执行一次规则扫描。
     *
     * 步骤：
     * 1. 读取后台启用的自动跟进规则。
     * 2. 扫描符合规则的求职投递记录。
     * 3. 创建提醒或工作流任务，具体幂等逻辑放在服务层统一处理。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scanFollowUpRules() {
        try {
            int createdCount = adminFollowUpAgentService.scanEnabledRules();
            if (createdCount > 0) {
                log.info("求职跟进 Agent 本次创建提醒数量：{}", createdCount);
            }
        } catch (Exception exception) {
            log.warn("求职跟进 Agent 定时扫描失败：{}", exception.getMessage(), exception);
        }
    }
}
