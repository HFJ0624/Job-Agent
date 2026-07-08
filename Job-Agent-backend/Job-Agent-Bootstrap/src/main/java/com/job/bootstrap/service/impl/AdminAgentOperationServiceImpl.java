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
 * <p>核心职责：为后台运营人员提供 Agent 运行状况的宏观视图，
 * 聚合 AI 日报生成、邮件发送、行动项执行等多维度指标，帮助快速定位运营异常。</p>
 *
 * <p>所属业务模块：Agent 运营中心 - 运营看板</p>
 *
 * <p>主要调用链：
 * AdminAgentOperationController → {@link AdminAgentOperationServiceImpl#dashboard} →
 * AgentDailyReportRecordMapper / AgentActionItemMapper → 返回 AgentOperationDashboardVO</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AgentDailyReportRecordMapper} 获取 AI 日报生成与邮件发送记录</li>
 *   <li>依赖 {@link AgentActionItemMapper} 获取用户行动项执行数据</li>
 *   <li>依赖 {@link AdminAgentOperationStatsCalculator} 统一计算百分比，避免 Service 内混杂统计细节</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>第一版直接聚合日报表和行动项表，不新增统计宽表，避免引入额外同步任务。</li>
 *   <li>默认统计最近 7 天数据，适合观察 Agent 最近是否真的推动用户行动。</li>
 *   <li>后续如果数据量变大，再把这里升级成按天预聚合表。</li>
 * </ul></p>
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

    /**
     * 获取 Agent 运营看板数据。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>计算最近 7 天的时间起点，统一时间边界。</li>
     *   <li>批量查询日报记录和行动项数据。</li>
     *   <li>填充顶部指标卡（AI 日报生成、行动项总数、执行失败、邮件失败）。</li>
     *   <li>构造日报统计、行动项状态分布、来源分布、失败分类及最近失败记录。</li>
     * </ol>
     *
     * @return Agent 运营看板聚合数据
     */
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

    /**
     * 填充运营看板顶部核心指标卡。
     *
     * <p>分别统计日报生成成功率、行动项完成率、执行失败数、邮件失败数，
     * 并调用 {@link AdminAgentOperationStatsCalculator#rate} 计算百分比展示。</p>
     *
     * @param dashboard 待填充的看板 VO
     * @param reports 最近 7 天的日报记录列表
     * @param actions 最近 7 天的行动项列表
     */
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

    /**
     * 构造日报生成与邮件发送状态统计分布。
     *
     * @param reports 日报记录列表
     * @return 包含生成成功、生成失败、邮件成功、邮件邮件的统计列表
     */
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

    /**
     * 按行动项执行状态构造分组统计。
     *
     * @param actions 行动项列表
     * @return 各状态数量及占比统计
     */
    private List<AgentOperationStatVO> buildStatusStats(List<AgentActionItem> actions) {
        return buildGroupStats(actions, AgentActionItem::getActionStatus);
    }

    /**
     * 按行动类型构造失败分组统计，用于识别哪类行动最容易失败。
     *
     * @param actions 行动项列表
     * @return 各行动类型失败数量及占比统计
     */
    private List<AgentOperationStatVO> buildActionTypeFailureStats(List<AgentActionItem> actions) {
        List<AgentActionItem> failedActions = actions.stream()
                .filter(item -> ACTION_FAILED.equals(item.getActionStatus()))
                .toList();
        return buildGroupStats(failedActions, AgentActionItem::getActionType);
    }

    /**
     * 通用分组统计构造器，按指定分类器对行动项聚合计数并计算占比。
     *
     * @param actions 待统计的行动项列表
     * @param classifier 分类函数，如按状态或按类型分组
     * @return 分组统计列表，按出现次数自然排序
     */
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

    /**
     * 构造最近失败记录列表，聚合日报生成失败、邮件发送失败及行动执行失败。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>从日报记录中筛选生成错误或邮件错误的记录，最多取 10 条。</li>
     *   <li>从行动项中筛选存在执行错误的记录，最多取 10 条。</li>
     *   <li>按创建时间倒序合并，最终只保留最近 10 条用于看板展示。</li>
     * </ol>
     *
     * @param reports 日报记录列表
     * @param actions 行动项列表
     * @return 最近失败记录列表，按时间倒序
     */
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

    /**
     * 创建单条失败记录 VO。
     *
     * @param type 失败类型（如日报生成失败、邮件失败、行动执行失败）
     * @param userId 关联用户 ID
     * @param title 失败对象标题
     * @param reason 失败原因或异常信息
     * @param createTime 失败发生时间
     * @return 失败记录 VO
     */
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
