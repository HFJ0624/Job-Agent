package com.job.bootstrap.scheduler;

import com.job.bootstrap.service.AgentDailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agent 主动日报定时任务。
 *
 * 执行策略：
 * 1. 每天上午 9 点生成并发送日报，适合作为用户开始求职前的提醒。
 * 2. 服务层内部按 userId + reportDate 幂等更新，调度重复执行也不会产生多条日报。
 * 3. 调度器只记录总量日志，避免高频输出影响控制台可读性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDailyReportScheduler {

    private final AgentDailyReportService agentDailyReportService;

    /**
     * 每天 09:00 执行一次主动日报生成。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void generateDailyReports() {
        try {
            int count = agentDailyReportService.generateTodayForActiveUsers();
            if (count > 0) {
                log.info("Agent 主动日报本次生成数量：{}", count);
            }
        } catch (Exception exception) {
            log.warn("Agent 主动日报定时生成失败：{}", exception.getMessage(), exception);
        }
    }
}
