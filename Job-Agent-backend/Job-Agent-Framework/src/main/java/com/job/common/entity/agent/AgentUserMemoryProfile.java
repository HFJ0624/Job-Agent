package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 用户长期记忆画像实体
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. agent_long_term_memory 保存“可追溯的记忆事实”，这张表保存“压缩后的用户画像”。
 * 2. 每轮对话不能把所有长期记忆都塞进 Prompt，否则 token 会越来越大。
 * 3. 因此这里把高频、稳定、强约束的记忆压缩成短文本，作为每轮默认注入的小上下文。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_user_memory_profile")
public class AgentUserMemoryProfile extends BaseEntity {

    /**
     * 画像所属用户 ID。
     */
    private Long userId;

    /**
     * 压缩后的用户画像摘要。
     *
     * 示例:
     * - 助手称呼: 黄锋森AI助手
     * - 求职偏好: 北京 Java 后端，最低薪资 20k
     * - 回答偏好: 简洁、直接
     */
    private String profileSummary;

    /**
     * 参与本次画像构建的有效记忆数量。
     */
    private Integer memoryCount;

    /**
     * 画像版本号。
     *
     * 第一版先使用自增整数，后续如果接入异步画像重建或多策略画像，可以用它做乐观识别。
     */
    private Integer profileVersion;

    /**
     * 最近一次画像重建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastBuildTime;

    /**
     * 画像状态。
     *
     * ACTIVE: 可注入 Prompt
     * DISABLED: 后台禁用，不再注入
     */
    private String status;
}
