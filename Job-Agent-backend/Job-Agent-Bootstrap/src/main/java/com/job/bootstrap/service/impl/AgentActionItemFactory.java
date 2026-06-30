package com.job.bootstrap.service.impl;

import com.job.common.entity.agent.AgentActionItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Agent 行动项工厂。
 *
 * 说明：
 * 1. 工厂只负责把不同来源的建议转换成统一行动项，不直接写数据库。
 * 2. V1 先接入 AI 日报 topActions，后续 HR 回复识别、跟进 Agent、面试复盘也可以复用同一入口。
 * 3. actionKey 在这里生成，保证同一来源重复生成时服务层可以幂等跳过。
 */
@Component
public class AgentActionItemFactory {

    private static final int NOT_DELETED = 0;
    private static final String SOURCE_DAILY_REPORT = "DAILY_REPORT";
    private static final String ACTION_TYPE_MANUAL_CONFIRM = "MANUAL_CONFIRM";
    private static final String STATUS_PENDING = "PENDING";
    private static final String PRIORITY_NORMAL = "NORMAL";
    private static final String TARGET_AGENT_INBOX = "/agent-inbox";

    /**
     * 从 AI 日报 topActions 生成行动项。
     *
     * @param userId 用户 ID
     * @param reportId 日报 ID
     * @param topActions 模型输出的今天最该做的事项
     * @return 待落库行动项
     */
    public List<AgentActionItem> fromDailyReportTopActions(Long userId, Long reportId, List<String> topActions) {
        List<AgentActionItem> items = new ArrayList<>();
        if (userId == null || reportId == null || topActions == null || topActions.isEmpty()) {
            return items;
        }

        Date now = new Date();
        for (int index = 0; index < topActions.size(); index++) {
            String action = topActions.get(index);
            if (!StringUtils.hasText(action)) {
                continue;
            }

            /*
             * 1. actionKey 使用来源 + 来源 ID + 序号，保证同一份日报重复生成时不会重复写入。
             */
            AgentActionItem item = new AgentActionItem();
            item.setUserId(userId);
            item.setActionKey(SOURCE_DAILY_REPORT + "_" + reportId + "_" + (index + 1));
            item.setSourceType(SOURCE_DAILY_REPORT);
            item.setSourceId(reportId);
            item.setActionType(ACTION_TYPE_MANUAL_CONFIRM);
            item.setActionTitle(action.trim());
            item.setActionDesc("来自今日 AI 求职日报的建议行动");
            item.setPriority(index == 0 ? "HIGH" : PRIORITY_NORMAL);
            item.setActionStatus(STATUS_PENDING);
            item.setTargetPath(TARGET_AGENT_INBOX);
            item.setIsDeleted(NOT_DELETED);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            items.add(item);
        }
        return items;
    }
}
