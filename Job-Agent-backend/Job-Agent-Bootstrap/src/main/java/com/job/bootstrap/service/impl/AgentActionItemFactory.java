package com.job.bootstrap.service.impl;

import com.job.common.entity.agent.AgentActionItem;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Agent 行动项工厂。
 *
 * 说明：
 * 1. 工厂只负责把不同来源的建议转换成统一行动项，不直接写数据库。
 * 2. 旧版 topActions 仍然会转换成 MANUAL_CONFIRM，保证历史 Prompt 不会失效。
 * 3. 新版 executableActions 会按白名单落成可执行 actionType，用户确认后才能真正联动业务。
 */
@Component
public class AgentActionItemFactory {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String ACTION_TYPE_MANUAL_CONFIRM = "MANUAL_CONFIRM";
    private static final String STATUS_PENDING = "PENDING";
    private static final String PRIORITY_HIGH = "HIGH";
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String PRIORITY_LOW = "LOW";
    private static final String TARGET_AGENT_INBOX = "/agent-inbox";

    private static final Set<String> EXECUTABLE_ACTION_TYPES = Set.of(
            "REMINDER_CREATE",
            "REMINDER_DONE",
            "LEARNING_PLAN_DONE",
            "WRONG_QUESTION_REVIEWED",
            "WRONG_QUESTION_MASTERED",
            "WORKFLOW_TASK_CREATE"
    );

    /**
     * 从 AI 日报 topActions 生成旧版人工确认行动项。
     *
     * @param userId 用户 ID
     * @param reportId 日报 ID
     * @param topActions 模型输出的今天最该做的事
     * @return 待落库行动项
     */
    public List<AgentActionItem> fromDailyReportTopActions(Long userId, Long reportId, List<String> topActions) {
        List<AgentActionItem> items = new ArrayList<>();
        if (userId == null || reportId == null || topActions == null || topActions.isEmpty()) {
            return items;
        }

        for (int index = 0; index < topActions.size(); index++) {
            String action = topActions.get(index);
            if (!StringUtils.hasText(action)) {
                continue;
            }

            /*
             * 步骤：
             * 1. 旧版只有文字建议，没有安全的业务参数，所以统一保持 MANUAL_CONFIRM。
             * 2. actionKey 继续沿用旧格式，避免同一份日报重复生成时插入重复行动项。
             */
            ExecutableActionSpec spec = new ExecutableActionSpec();
            spec.setActionTitle(action.trim());
            spec.setActionDesc("来自今日 AI 求职日报的建议行动");
            spec.setActionType(ACTION_TYPE_MANUAL_CONFIRM);
            spec.setPriority(index == 0 ? PRIORITY_HIGH : PRIORITY_NORMAL);
            spec.setTargetPath(TARGET_AGENT_INBOX);
            items.add(buildDailyReportItem(userId, reportId, index, false, spec));
        }
        return items;
    }

    /**
     * 从 AI 日报 executableActions 生成新版结构化行动项。
     *
     * @param userId 用户 ID
     * @param reportId 日报 ID
     * @param actions 模型输出的结构化行动项
     * @return 待落库行动项
     */
    public List<AgentActionItem> fromDailyReportExecutableActions(
            Long userId,
            Long reportId,
            List<ExecutableActionSpec> actions
    ) {
        List<AgentActionItem> items = new ArrayList<>();
        if (userId == null || reportId == null || actions == null || actions.isEmpty()) {
            return items;
        }

        for (int index = 0; index < actions.size(); index++) {
            ExecutableActionSpec spec = actions.get(index);
            if (spec == null || !StringUtils.hasText(spec.getActionTitle())) {
                continue;
            }
            items.add(buildDailyReportItem(userId, reportId, index, true, spec));
        }
        return items;
    }

    private AgentActionItem buildDailyReportItem(
            Long userId,
            Long reportId,
            int index,
            boolean executable,
            ExecutableActionSpec spec
    ) {
        Date now = new Date();
        AgentActionItem item = new AgentActionItem();
        item.setUserId(userId);
        item.setActionKey(SOURCE_DAILY_REPORT + "_" + reportId + "_" + (executable ? "EXEC_" : "") + (index + 1));
        item.setSourceType(SOURCE_DAILY_REPORT);
        item.setSourceId(reportId);
        item.setActionType(normalizeActionType(spec.getActionType()));
        item.setBizType(trimToNull(spec.getBizType()));
        item.setBizId(spec.getBizId());
        item.setActionTitle(spec.getActionTitle().trim());
        item.setActionDesc(trimToDefault(spec.getActionDesc(), "来自今日 AI 求职日报的建议行动"));
        item.setPriority(normalizePriority(spec.getPriority(), index));
        item.setActionStatus(STATUS_PENDING);
        item.setTargetPath(trimToDefault(spec.getTargetPath(), TARGET_AGENT_INBOX));
        item.setActionPayload(trimToNull(spec.getActionPayload()));
        item.setIsDeleted(NOT_DELETED);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }

    private String normalizeActionType(String actionType) {
        if (!StringUtils.hasText(actionType)) {
            return ACTION_TYPE_MANUAL_CONFIRM;
        }
        String value = actionType.trim();
        if (ACTION_TYPE_MANUAL_CONFIRM.equals(value) || EXECUTABLE_ACTION_TYPES.contains(value)) {
            return value;
        }
        return ACTION_TYPE_MANUAL_CONFIRM;
    }

    private String normalizePriority(String priority, int index) {
        if (PRIORITY_HIGH.equals(priority) || PRIORITY_NORMAL.equals(priority) || PRIORITY_LOW.equals(priority)) {
            return priority;
        }
        return index == 0 ? PRIORITY_HIGH : PRIORITY_NORMAL;
    }

    private String trimToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 模型输出的结构化行动项。
     */
    @Data
    public static class ExecutableActionSpec {

        private String actionTitle;

        private String actionDesc;

        private String actionType;

        private String bizType;

        private Long bizId;

        private String actionPayload;

        private String priority;

        private String targetPath;
    }
}
