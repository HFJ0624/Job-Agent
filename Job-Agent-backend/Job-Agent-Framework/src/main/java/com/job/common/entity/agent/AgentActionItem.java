package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Agent 行动确认项。
 *
 * 说明：
 * 1. 行动项用于承接 Agent 给用户的建议，让建议可以被确认、忽略、稍后处理和追踪。
 * 2. V1 只更新本表状态，不自动修改原业务表，避免 AI 建议误触发业务数据变更。
 * 3. actionKey 用于同一来源下去重，例如 DAILY_REPORT_11_1 表示日报 11 的第 1 个行动。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_action_item")
public class AgentActionItem extends BaseEntity {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 行动唯一键，用于幂等去重。
     */
    private String actionKey;

    /**
     * 来源类型，例如 DAILY_REPORT、HR_REPLY、FOLLOW_UP_AGENT。
     */
    private String sourceType;

    /**
     * 来源业务 ID，例如日报 ID。
     */
    private Long sourceId;

    /**
     * 行动类型。V1 统一先用 MANUAL_CONFIRM。
     */
    private String actionType;

    /**
     * 需要联动执行的业务类型，例如 REMINDER、LEARNING_PLAN_ITEM、WRONG_QUESTION。
     */
    private String bizType;

    /**
     * 需要联动执行的业务 ID。
     */
    private Long bizId;

    /**
     * 行动标题。
     */
    private String actionTitle;

    /**
     * 行动说明或推荐理由。
     */
    private String actionDesc;

    /**
     * 优先级：HIGH / NORMAL / LOW。
     */
    private String priority;

    /**
     * 状态：PENDING / DONE / IGNORED / SNOOZED / FAILED。
     */
    private String actionStatus;

    /**
     * 点击去处理时跳转的前端路径。
     */
    private String targetPath;

    /**
     * 执行参数 JSON。创建提醒等动作会用到更复杂的参数。
     */
    private String actionPayload;

    /**
     * 执行失败原因。
     */
    private String executeError;

    /**
     * 稍后处理时间。
     */
    private Date snoozeUntil;

    /**
     * 用户备注。
     */
    private String note;

    /**
     * 完成时间。
     */
    private Date doneTime;
}
