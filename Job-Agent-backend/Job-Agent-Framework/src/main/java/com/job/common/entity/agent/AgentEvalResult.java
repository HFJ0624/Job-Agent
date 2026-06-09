package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能: Agent 评测结果实体
 * 设计说明:
 * 1. 每运行一次评测，就保存一条结果。
 * 2. 这样可以观察 Prompt、Tool、模型版本变化后，Agent 能力有没有退化。
 * 日期: 2026/6/9 16:34
 */
@Data
@TableName("agent_eval_result")
public class AgentEvalResult{

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对应的评测用例ID。
     */
    private Long caseId;

    /**
     * 测试用户ID。
     */
    private Long userId;

    /**
     * 本次运行生成或复用的会话ID。
     */
    private Long conversationId;

    /**
     * 本次输入。
     */
    private String inputMessage;

    /**
     * Agent 实际回答。
     */
    private String actualAnswer;

    /**
     * 实际调用的工具列表。
     * 建议存 JSON 字符串。
     */
    private String actualTools;

    /**
     * 是否通过。
     * 1 通过，0 失败。
     */
    private Integer passStatus;

    /**
     * 失败原因。
     */
    private String failReason;

    /**
     * 耗时，单位毫秒。
     */
    private Long costTime;

    private Date createTime;
}
