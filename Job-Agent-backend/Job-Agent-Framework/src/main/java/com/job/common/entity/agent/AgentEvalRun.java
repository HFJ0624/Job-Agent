package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent Eval 批量运行记录，保存一次回归任务的整体指标
 * 日期:2026/6/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_eval_run")
public class AgentEvalRun extends BaseEntity {

    private Long datasetId;
    private String runName;
    private String runType;
    private Integer totalCount;
    private Integer passCount;
    private Integer failCount;
    private BigDecimal toolAccuracy;
    private BigDecimal paramAccuracy;
    private BigDecimal ragHitRate;
    private BigDecimal answerQualityAvg;
    private Long avgCostTime;
    /**
     * 是否基准批次：0否，1是。
     */
    private Integer baselineFlag;

    /**
     * 本批次用于对比的基准批次ID。
     */
    private Long compareRunId;

    /**
     * 通过率相对基准的变化值。
     */
    private BigDecimal passRateDelta;

    /**
     * 工具准确率相对基准的变化值。
     */
    private BigDecimal toolAccuracyDelta;

    /**
     * 参数准确率相对基准的变化值。
     */
    private BigDecimal paramAccuracyDelta;

    /**
     * RAG 命中率相对基准的变化值。
     */
    private BigDecimal ragHitRateDelta;

    /**
     * 回答质量均分相对基准的变化值。
     */
    private BigDecimal answerQualityDelta;

    /**
     * 失败分类统计 JSON。
     */
    private String failureStatsJson;

    private String status;
    private Date startTime;
    private Date endTime;
    private String failReason;
}
