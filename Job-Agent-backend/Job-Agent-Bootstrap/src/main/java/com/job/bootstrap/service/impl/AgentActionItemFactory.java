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
 * Agent 行动项工厂，负责把不同来源的建议转换为统一行动项实体。
 *
 * <p>核心职责：
 * 接收 AI 日报的 topActions（旧版文字建议）或 executableActions（新版结构化建议），
 * 转换为 AgentActionItem 实体并填充 actionKey、actionType、priority 等字段。
 * 工厂本身不直接写数据库，落库由调用方（如 AgentDailyReportServiceImpl）负责。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Action 子模块（行动项构建层）。</p>
 *
 * <p>主要调用链：
 * AgentDailyReportServiceImpl.createActionItemsFromDailyReport
 * -> AgentActionItemFactory.fromDailyReportExecutableActions / fromDailyReportTopActions
 * -> buildDailyReportItem（统一构建实体）
 * -> normalizeActionType（白名单校验）/ normalizePriority（优先级归一）
 * -> 返回 List&lt;AgentActionItem&gt; 给日报 Service 落库</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>工厂只负责把不同来源的建议转换成统一行动项，不直接写数据库；</li>
 *   <li>旧版 topActions 仍然会转换成 MANUAL_CONFIRM，保证历史 Prompt 不会失效；</li>
 *   <li>新版 executableActions 会按白名单落成可执行 actionType，用户确认后才能真正联动业务；</li>
 *   <li>actionType 不在白名单时降级为 MANUAL_CONFIRM，避免 AI 输出未受控 actionType 误改业务数据。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 工厂只负责把不同来源的建议转换成统一行动项，不直接写数据库。
 * 2. 旧版 topActions 仍然会转换成 MANUAL_CONFIRM，保证历史 Prompt 不会失效。
 * 3. 新版 executableActions 会按白名单落成可执行 actionType，用户确认后才能真正联动业务。</p>
 *
 * 作者: hfj
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
     * <p>核心处理流程：
     * 1. 校验 userId、reportId、topActions 非空，避免生成空行动项；
     * 2. 遍历 topActions，跳过空字符串；
     * 3. 旧版只有文字建议，没有安全的业务参数，所以统一保持 MANUAL_CONFIRM；
     * 4. actionKey 沿用旧格式，避免同一份日报重复生成时插入重复行动项；
     * 5. 第一条建议默认 HIGH 优先级，其余为 NORMAL。</p>
     *
     * @param userId      当前用户 ID
     * @param reportId    日报 ID，作为 sourceId 写入行动项
     * @param topActions  模型输出的今天最该做的事列表
     * @return 待落库行动项列表，输入为空时返回空列表
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
     * <p>核心处理流程：
     * 1. 校验 userId、reportId、actions 非空，避免生成空行动项；
     * 2. 遍历 actions，跳过 actionTitle 为空的项；
     * 3. 调用 buildDailyReportItem 统一构建实体，executable=true 标记为可执行；
     * 4. actionType 由 normalizeActionType 做白名单校验，未识别时降级为 MANUAL_CONFIRM。</p>
     *
     * @param userId  当前用户 ID
     * @param reportId 日报 ID，作为 sourceId 写入行动项
     * @param actions  模型输出的结构化行动项列表
     * @return 待落库行动项列表，输入为空时返回空列表
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

    /**
     * 统一构建日报来源的行动项实体，填充 actionKey、actionType、priority 等字段。
     *
     * <p>核心处理流程：
     * 1. 生成 actionKey，executable=true 时加 EXEC_ 前缀避免与旧版 topActions 冲突；
     * 2. actionType 由 normalizeActionType 校验，未在白名单时降级为 MANUAL_CONFIRM；
     * 3. priority 由 normalizePriority 归一，非法值时第一条默认 HIGH，其余 NORMAL；
     * 4. targetPath 缺失时回退到 /agent-inbox，保证前端始终有可跳转入口。</p>
     *
     * @param userId     当前用户 ID
     * @param reportId   日报 ID，作为 sourceId
     * @param index      行动项在原列表中的序号，用于生成 actionKey 与默认优先级
     * @param executable 是否为可执行行动项，影响 actionKey 前缀
     * @param spec       模型输出的结构化行动项规格
     * @return 已填充字段的 AgentActionItem 实体，未落库
     */
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

    /**
     * 校验 actionType 是否在白名单内，未识别时降级为 MANUAL_CONFIRM。
     *
     * <p>说明：白名单外的 actionType 一律降级为人工确认，避免 AI 输出未受控 actionType 误改业务数据。</p>
     *
     * @param actionType 待校验 actionType
     * @return 白名单内的 actionType，或降级后的 MANUAL_CONFIRM
     */
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

    /**
     * 归一化优先级，非法值时第一条默认 HIGH，其余 NORMAL。
     *
     * @param priority 待校验优先级
     * @param index    行动项序号，用于在优先级缺失时回退
     * @return HIGH / NORMAL / LOW 之一
     */
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
