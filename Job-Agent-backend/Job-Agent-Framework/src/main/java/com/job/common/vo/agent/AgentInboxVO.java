package com.job.common.vo.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 用户端 Agent Inbox 聚合视图。
 *
 * 说明：
 * 1. Inbox 不新建一套业务状态，而是聚合提醒、HR 回复识别、错题本、学习计划等已有模块。
 * 2. 每条 item 只描述“用户现在该处理什么”和“点击后去哪里处理”。
 * 3. 真正的完成、忽略、确认动作仍由各业务模块自己的接口负责，避免第一版引入重复状态。
 */
@Data
public class AgentInboxVO {

    /**
     * 待办总数。
     */
    private Integer totalCount = 0;

    /**
     * 高优先级待办数量。
     */
    private Integer highPriorityCount = 0;

    /**
     * 今日到期或已到期数量。
     */
    private Integer dueCount = 0;

    /**
     * 普通建议数量。
     */
    private Integer normalCount = 0;

    /**
     * 页面顶部摘要文案。
     */
    private String summaryText;

    /**
     * 待办列表。
     */
    private List<Item> items = new ArrayList<>();

    /**
     * 单条 Inbox 待办。
     */
    @Data
    public static class Item {

        /**
         * 前端渲染使用的稳定唯一键，例如 HR_REPLY_CONFIRM_12。
         */
        private String itemKey;

        /**
         * 待办类型。
         */
        private String itemType;

        /**
         * 类型中文。
         */
        private String itemTypeDesc;

        /**
         * HIGH / NORMAL / LOW。
         */
        private String priority;

        /**
         * 待办标题。
         */
        private String title;

        /**
         * 待办说明。
         */
        private String description;

        /**
         * 主按钮文案。
         */
        private String actionText;

        /**
         * 点击后跳转路径。
         */
        private String targetPath;

        /**
         * 原始业务表 ID。
         */
        private Long sourceId;

        /**
         * 关联求职记录 ID。
         */
        private Long applicationId;

        /**
         * 关联沟通记录 ID。
         */
        private Long communicationId;

        /**
         * 关联岗位 ID。
         */
        private Long jobId;

        /**
         * 公司名称快照。
         */
        private String companyName;

        /**
         * 岗位名称快照。
         */
        private String jobTitle;

        /**
         * 到期时间或事件时间，用于排序和展示。
         */
        private Date dueTime;

        /**
         * 创建时间。
         */
        private Date createTime;
    }
}
