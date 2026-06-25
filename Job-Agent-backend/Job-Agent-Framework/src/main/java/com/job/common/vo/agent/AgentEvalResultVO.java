package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentEvalResult;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent Eval 单条结果展示对象
 * 日期:2026/6/24
 */
@Data
public class AgentEvalResultVO {
    private Long id;
    private Long runId;
    private Long datasetId;
    private Long caseId;
    private Long userId;
    private Long conversationId;
    private String inputMessage;
    private String evalType;
    private String actualAnswer;
    private String actualTools;
    private String expectedToolName;
    private Integer toolSelectPass;
    private String expectedToolParamsJson;
    private String actualToolParamsJson;
    private Integer toolParamPass;
    private Integer ragHitPass;
    private Integer ragHitRank;
    private String ragResultsJson;
    private Integer answerKeywordPass;
    private BigDecimal answerQualityScore;
    private BigDecimal judgeScore;
    private Integer judgePass;
    private String judgeReason;
    private String judgeDetailJson;
    private Long baselineResultId;
    private BigDecimal answerScoreDelta;
    private Integer passStatus;
    private String failReason;
    private String failureType;
    private String traceId;
    private Long costTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentEvalResultVO from(AgentEvalResult entity) {
        if (entity == null) {
            return null;
        }
        AgentEvalResultVO vo = new AgentEvalResultVO();
        vo.setId(entity.getId());
        vo.setRunId(entity.getRunId());
        vo.setDatasetId(entity.getDatasetId());
        vo.setCaseId(entity.getCaseId());
        vo.setUserId(entity.getUserId());
        vo.setConversationId(entity.getConversationId());
        vo.setInputMessage(entity.getInputMessage());
        vo.setEvalType(entity.getEvalType());
        vo.setActualAnswer(entity.getActualAnswer());
        vo.setActualTools(entity.getActualTools());
        vo.setExpectedToolName(entity.getExpectedToolName());
        vo.setToolSelectPass(entity.getToolSelectPass());
        vo.setExpectedToolParamsJson(entity.getExpectedToolParamsJson());
        vo.setActualToolParamsJson(entity.getActualToolParamsJson());
        vo.setToolParamPass(entity.getToolParamPass());
        vo.setRagHitPass(entity.getRagHitPass());
        vo.setRagHitRank(entity.getRagHitRank());
        vo.setRagResultsJson(entity.getRagResultsJson());
        vo.setAnswerKeywordPass(entity.getAnswerKeywordPass());
        vo.setAnswerQualityScore(entity.getAnswerQualityScore());
        vo.setJudgeScore(entity.getJudgeScore());
        vo.setJudgePass(entity.getJudgePass());
        vo.setJudgeReason(entity.getJudgeReason());
        vo.setJudgeDetailJson(entity.getJudgeDetailJson());
        vo.setBaselineResultId(entity.getBaselineResultId());
        vo.setAnswerScoreDelta(entity.getAnswerScoreDelta());
        vo.setPassStatus(entity.getPassStatus());
        vo.setFailReason(entity.getFailReason());
        vo.setFailureType(entity.getFailureType());
        vo.setTraceId(entity.getTraceId());
        vo.setCostTime(entity.getCostTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
