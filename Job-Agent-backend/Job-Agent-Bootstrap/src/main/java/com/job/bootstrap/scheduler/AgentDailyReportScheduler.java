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
 * 1. 每分钟轻量扫描一次订阅配置，只有 sendTime 命中当前 HH:mm 的用户才会生成日报。
 * 2. 服务层内部使用 lastGenerateDate 去重，避免同一分钟或重启后重复发送。
 * 3. 调度器只在有生成数量或异常时输出日志，避免控制台被定时任务刷屏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDailyReportScheduler {

    private final AgentDailyReportService agentDailyReportService;

    /**
     * 每分钟扫描一次到点订阅。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void generateDailyReports() {
        try {
            int count = agentDailyReportService.generateDueSubscriptions();
            if (count > 0) {
                log.info("Agent 主动日报本次生成数量：{}", count);
            }
        } catch (Exception exception) {
            log.warn("Agent 主动日报定时生成失败：{}", exception.getMessage(), exception);
        }
    }
}
