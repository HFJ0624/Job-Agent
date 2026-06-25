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
     * 评测运行批次ID。
     */
    private Long runId;

    /**
     * 数据集ID。
     */
    private Long datasetId;

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
     * 评测类型。
     */
    private String evalType;

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
     * 期望工具名称。
     */
    private String expectedToolName;

    /**
     * 工具选择是否通过。
     */
    private Integer toolSelectPass;

    /**
     * 期望工具参数 JSON。
     */
    private String expectedToolParamsJson;

    /**
     * 实际工具参数 JSON。
     */
    private String actualToolParamsJson;

    /**
     * 工具参数是否通过。
     */
    private Integer toolParamPass;

    /**
     * RAG 是否命中。
     */
    private Integer ragHitPass;

    /**
     * RAG 命中排名。
     */
    private Integer ragHitRank;

    /**
     * RAG 召回结果 JSON。
     */
    private String ragResultsJson;

    /**
     * 回答关键词是否通过。
     */
    private Integer answerKeywordPass;

    /**
     * 回答质量分。
     */
    private java.math.BigDecimal answerQualityScore;

    /**
     * LLM-as-Judge 评分。
     */
    private java.math.BigDecimal judgeScore;

    /**
     * LLM-as-Judge 是否通过。
     */
    private Integer judgePass;

    /**
     * LLM-as-Judge 评分原因。
     */
    private String judgeReason;

    /**
     * LLM-as-Judge 维度详情 JSON。
     */
    private String judgeDetailJson;

    /**
     * 对应的基准结果ID。
     */
    private Long baselineResultId;

    /**
     * 回答质量分相对基准的变化值。
     */
    private java.math.BigDecimal answerScoreDelta;

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
     * 失败分类。
     */
    private String failureType;

    /**
     * Trace ID。
     */
    private String traceId;

    /**
     * 耗时，单位毫秒。
     */
    private Long costTime;

    private Date createTime;
}
