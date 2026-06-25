package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentEvalRun;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent Eval 运行批次展示对象
 * 日期:2026/6/24
 */
@Data
public class AgentEvalRunVO {
    private Long id;
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
    private Integer baselineFlag;
    private Long compareRunId;
    private BigDecimal passRateDelta;
    private BigDecimal toolAccuracyDelta;
    private BigDecimal paramAccuracyDelta;
    private BigDecimal ragHitRateDelta;
    private BigDecimal answerQualityDelta;
    private String failureStatsJson;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date endTime;
    private String failReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentEvalRunVO from(AgentEvalRun entity) {
        if (entity == null) {
            return null;
        }
        AgentEvalRunVO vo = new AgentEvalRunVO();
        vo.setId(entity.getId());
        vo.setDatasetId(entity.getDatasetId());
        vo.setRunName(entity.getRunName());
        vo.setRunType(entity.getRunType());
        vo.setTotalCount(entity.getTotalCount());
        vo.setPassCount(entity.getPassCount());
        vo.setFailCount(entity.getFailCount());
        vo.setToolAccuracy(entity.getToolAccuracy());
        vo.setParamAccuracy(entity.getParamAccuracy());
        vo.setRagHitRate(entity.getRagHitRate());
        vo.setAnswerQualityAvg(entity.getAnswerQualityAvg());
        vo.setAvgCostTime(entity.getAvgCostTime());
        vo.setBaselineFlag(entity.getBaselineFlag());
        vo.setCompareRunId(entity.getCompareRunId());
        vo.setPassRateDelta(entity.getPassRateDelta());
        vo.setToolAccuracyDelta(entity.getToolAccuracyDelta());
        vo.setParamAccuracyDelta(entity.getParamAccuracyDelta());
        vo.setRagHitRateDelta(entity.getRagHitRateDelta());
        vo.setAnswerQualityDelta(entity.getAnswerQualityDelta());
        vo.setFailureStatsJson(entity.getFailureStatsJson());
        vo.setStatus(entity.getStatus());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setFailReason(entity.getFailReason());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
