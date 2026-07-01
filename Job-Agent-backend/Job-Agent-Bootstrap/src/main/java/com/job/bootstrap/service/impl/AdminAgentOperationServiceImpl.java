package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentActionItemMapper;
import com.job.bootstrap.mapper.AgentDailyReportRecordMapper;
import com.job.bootstrap.service.AdminAgentOperationService;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.entity.agent.AgentDailyReportRecord;
import com.job.common.vo.agent.AgentOperationDashboardVO;
import com.job.common.vo.agent.AgentOperationFailureVO;
import com.job.common.vo.agent.AgentOperationMetricVO;
import com.job.common.vo.agent.AgentOperationStatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Agent 运营看板服务实现。
 *
 * 说明：
 * 1. 第一版直接聚合日报表和行动项表，不新增统计宽表，避免引入额外同步任务。
 * 2. 默认统计最近 7 天数据，适合观察 Agent 最近是否真的推动用户行动。
 * 3. 后续如果数据量变大，再把这里升级成按天预聚合表。
 */
@Service
@RequiredArgsConstructor
public class AdminAgentOperationServiceImpl implements AdminAgentOperationService {

    private static final int NOT_DELETED = 0;
    private static final String GENERATION_SUCCESS = "SUCCESS";
    private static final String GENERATION_FAILED = "FAILED";
    private static final String EMAIL_SENT = "SENT";
    private static final String EMAIL_FAILED = "FAILED";
    private static final String ACTION_DONE = "DONE";
    private static final String ACTION_FAILED = "FAILED";

    private final AgentDailyReportRecordMapper reportMapper;
    private final AgentActionItemMapper actionItemMapper;
    private final AdminAgentOperationStatsCalculator calculator;

    @Override
    public AgentOperationDashboardVO dashboard() {
        Date startTime = Date.from(LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<AgentDailyReportRecord> reports = reportMapper.selectList(
                new LambdaQueryWrapper<AgentDailyReportRecord>()
                        .eq(AgentDailyReportRecord::getIsDeleted, NOT_DELETED)
                        .ge(AgentDailyReportRecord::getCreateTime, startTime)
        );
        List<AgentActionItem> actions = actionItemMapper.selectList(
                new LambdaQueryWrapper<AgentActionItem>()
                        .eq(AgentActionItem::getIsDeleted, NOT_DELETED)
                        .ge(AgentActionItem::getCreateTime, startTime)
        );

        AgentOperationDashboardVO dashboard = new AgentOperationDashboardVO();
        fillMetrics(dashboard, reports, actions);
        dashboard.setReportStats(buildReportStats(reports));
        dashboard.setActionStatusStats(buildStatusStats(actions));
        dashboard.setActionSourceStats(buildGroupStats(actions, AgentActionItem::getSourceType));
        dashboard.setActionTypeFailureStats(buildActionTypeFailureStats(actions));
        dashboard.setRecentFailures(buildRecentFailures(reports, actions));
        return dashboard;
    }

    private void fillMetrics(
            AgentOperationDashboardVO dashboard,
            List<AgentDailyReportRecord> reports,
            List<AgentActionItem> actions
    ) {
        long reportTotal = reports.size();
        long reportSuccess = reports.stream().filter(item -> GENERATION_SUCCESS.equals(item.getGenerationStatus())).count();
        long emailFailed = reports.stream().filter(item -> EMAIL_FAILED.equals(item.getEmailStatus())).count();
        long actionTotal = actions.size();
        long actionDone = actions.stream().filter(item -> ACTION_DONE.equals(item.getActionStatus())).count();
        long actionFailed = actions.stream().filter(item -> ACTION_FAILED.equals(item.getActionStatus())).count();

        dashboard.getMetrics().add(new AgentOperationMetricVO(
                "AI 日报生成",
                reportTotal,
                "成功率 " + calculator.rate(reportSuccess, reportTotal) + "%",
                reportTotal == reportSuccess ? "success" : "warning"
        ));
        dashboard.getMetrics().add(new AgentOperationMetricVO(
                "行动项总数",
                actionTotal,
                "完成率 " + calculator.rate(actionDone, actionTotal) + "%",
                actionDone > 0 ? "success" : "info"
        ));
        dashboard.getMetrics().add(new AgentOperationMetricVO(
                "执行失败",
                actionFailed,
                "行动执行失败数量",
                actionFailed > 0 ? "danger" : "success"
        ));
        dashboard.getMetrics().add(new AgentOperationMetricVO(
                "邮件失败",
                emailFailed,
                "日报邮件发送失败数量",
                emailFailed > 0 ? "danger" : "success"
        ));
    }

    private List<AgentOperationStatVO> buildReportStats(List<AgentDailyReportRecord> reports) {
        long total = reports.size();
        long success = reports.stream().filter(item -> GENERATION_SUCCESS.equals(item.getGenerationStatus())).count();
        long failed = reports.stream().filter(item -> GENERATION_FAILED.equals(item.getGenerationStatus())).count();
        long emailSent = reports.stream().filter(item -> EMAIL_SENT.equals(item.getEmailStatus())).count();
        long emailFailed = reports.stream().filter(item -> EMAIL_FAILED.equals(item.getEmailStatus())).count();
        return List.of(
                new AgentOperationStatVO("生成成功", success, calculator.rate(success, total)),
                new AgentOperationStatVO("生成失败", failed, calculator.rate(failed, total)),
                new AgentOperationStatVO("邮件成功", emailSent, calculator.rate(emailSent, total)),
                new AgentOperationStatVO("邮件失败", emailFailed, calculator.rate(emailFailed, total))
        );
    }

    private List<AgentOperationStatVO> buildStatusStats(List<AgentActionItem> actions) {
        return buildGroupStats(actions, AgentActionItem::getActionStatus);
    }

    private List<AgentOperationStatVO> buildActionTypeFailureStats(List<AgentActionItem> actions) {
        List<AgentActionItem> failedActions = actions.stream()
                .filter(item -> ACTION_FAILED.equals(item.getActionStatus()))
                .toList();
        return buildGroupStats(failedActions, AgentActionItem::getActionType);
    }

    private List<AgentOperationStatVO> buildGroupStats(
            List<AgentActionItem> actions,
            java.util.function.Function<AgentActionItem, String> classifier
    ) {
        long total = actions.size();
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (AgentActionItem action : actions) {
            String key = StringUtils.hasText(classifier.apply(action)) ? classifier.apply(action) : "UNKNOWN";
            countMap.put(key, countMap.getOrDefault(key, 0L) + 1);
        }
        List<AgentOperationStatVO> stats = new ArrayList<>();
        countMap.forEach((key, count) -> stats.add(new AgentOperationStatVO(key, count, calculator.rate(count, total))));
        return stats;
    }

    private List<AgentOperationFailureVO> buildRecentFailures(
            List<AgentDailyReportRecord> reports,
            List<AgentActionItem> actions
    ) {
        List<AgentOperationFailureVO> failures = new ArrayList<>();
        reports.stream()
                .filter(report -> StringUtils.hasText(report.getGenerationError()) || StringUtils.hasText(report.getEmailError()))
                .limit(10)
                .forEach(report -> {
                    if (StringUtils.hasText(report.getGenerationError())) {
                        failures.add(failure("日报生成失败", report.getUserId(), report.getReportTitle(), report.getGenerationError(), report.getCreateTime()));
                    }
                    if (StringUtils.hasText(report.getEmailError())) {
                        failures.add(failure("日报邮件失败", report.getUserId(), report.getReportTitle(), report.getEmailError(), report.getCreateTime()));
                    }
                });
        actions.stream()
                .filter(action -> StringUtils.hasText(action.getExecuteError()))
                .limit(10)
                .forEach(action -> failures.add(failure("行动执行失败", action.getUserId(), action.getActionTitle(), action.getExecuteError(), action.getCreateTime())));
        failures.sort((left, right) -> {
            Date leftTime = left.getCreateTime() == null ? new Date(0) : left.getCreateTime();
            Date rightTime = right.getCreateTime() == null ? new Date(0) : right.getCreateTime();
            return rightTime.compareTo(leftTime);
        });
        return failures.stream().limit(10).toList();
    }

    private AgentOperationFailureVO failure(String type, Long userId, String title, String reason, Date createTime) {
        AgentOperationFailureVO vo = new AgentOperationFailureVO();
        vo.setFailureType(type);
        vo.setUserId(userId);
        vo.setTitle(title);
        vo.setReason(reason);
        vo.setCreateTime(createTime);
        return vo;
    }
}
