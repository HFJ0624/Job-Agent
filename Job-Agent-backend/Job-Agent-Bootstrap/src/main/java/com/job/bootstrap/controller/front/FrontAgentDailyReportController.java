package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.AgentDailyReportService;
import com.job.common.dto.agent.AgentDailyReportSubscriptionSaveDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentDailyReportSubscriptionVO;
import com.job.common.vo.agent.AgentDailyReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 用户端 Agent 主动日报接口。
 *
 * 说明：
 * 1. 用户只能查看和生成自己的日报，userId 固定从登录态读取。
 * 2. 手动生成接口主要用于测试和用户即时刷新，不需要等待每天定时任务。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/front/agent-daily-reports")
public class FrontAgentDailyReportController {

    private final AgentDailyReportService agentDailyReportService;

    /**
     * 查询最近的 Agent 日报。
     */
    @GetMapping("/recent")
    public Result<List<AgentDailyReportVO>> recent(@RequestParam(defaultValue = "7") Integer limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(agentDailyReportService.listRecent(userId, limit), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查询当前用户日报订阅配置。
     */
    @GetMapping("/subscription")
    public Result<AgentDailyReportSubscriptionVO> subscription() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(agentDailyReportService.getSubscription(userId), ResultCodeEnum.SUCCESS);
    }

    /**
     * 保存当前用户日报订阅配置。
     */
    @PostMapping("/subscription")
    public Result<AgentDailyReportSubscriptionVO> saveSubscription(
            @RequestBody AgentDailyReportSubscriptionSaveDTO dto
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.build(agentDailyReportService.saveSubscription(userId, dto), ResultCodeEnum.SUCCESS);
    }

    /**
     * 手动生成今日日报。
     */
    @PostMapping("/today/generate")
    public Result<AgentDailyReportVO> generateToday(
            @RequestParam(required = false) Boolean sendEmail
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean shouldSendEmail = sendEmail == null
                ? Integer.valueOf(1).equals(agentDailyReportService.getSubscription(userId).getEmailEnabled())
                : Boolean.TRUE.equals(sendEmail);
        AgentDailyReportVO report = agentDailyReportService.generateForUser(userId, new Date(), shouldSendEmail);
        return Result.build(report, ResultCodeEnum.SUCCESS);
    }
}
