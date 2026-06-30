package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Agent Inbox 待办处理记录。
 *
 * 说明：
 * 1. Inbox 待办本身来自提醒、HR 回复识别、错题本等业务表，不在这里重复保存业务数据。
 * 2. 这张表只记录用户对某条聚合待办的处理态度，例如完成、忽略、稍后提醒。
 * 3. 聚合接口查询时会读取这张表，过滤已经完成/忽略/尚未到稍后时间的待办。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_inbox_action_record")
public class AgentInboxActionRecord extends BaseEntity {

    /**
     * 当前登录用户 ID，用于权限隔离。
     */
    private Long userId;

    /**
     * Inbox 聚合项唯一键，例如 HR_REPLY_CONFIRM_12。
     */
    private String itemKey;

    /**
     * 待办类型，例如 HR_REPLY_CONFIRM / REMINDER / WRONG_QUESTION_REVIEW。
     */
    private String itemType;

    /**
     * 原始业务表 ID。
     */
    private Long sourceId;

    /**
     * 处理状态：DONE / IGNORED / SNOOZED。
     */
    private String actionStatus;

    /**
     * 稍后提醒时间。只有 actionStatus = SNOOZED 时有意义。
     */
    private Date snoozeUntil;

    /**
     * 用户备注。
     */
    private String note;
}
