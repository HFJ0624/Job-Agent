package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentEvalCase;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent Eval 用例展示对象
 * 日期:2026/6/24
 */
@Data
public class AgentEvalCaseVO {
    private Long id;
    private Long datasetId;
    private String caseName;
    private Long userId;
    private String inputMessage;
    private String evalType;
    private String expectedIntent;
    private String expectedToolName;
    private String expectedToolParamsJson;
    private Long expectedRagDocumentId;
    private Long expectedRagChunkId;
    private String expectedRagKeywords;
    private String expectedAnswerKeywords;
    private BigDecimal minAnswerScore;
    private String tags;
    private Integer enableStatus;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentEvalCaseVO from(AgentEvalCase entity) {
        if (entity == null) {
            return null;
        }
        AgentEvalCaseVO vo = new AgentEvalCaseVO();
        vo.setId(entity.getId());
        vo.setDatasetId(entity.getDatasetId());
        vo.setCaseName(entity.getCaseName());
        vo.setUserId(entity.getUserId());
        vo.setInputMessage(entity.getInputMessage());
        vo.setEvalType(entity.getEvalType());
        vo.setExpectedIntent(entity.getExpectedIntent());
        vo.setExpectedToolName(entity.getExpectedToolName());
        vo.setExpectedToolParamsJson(entity.getExpectedToolParamsJson());
        vo.setExpectedRagDocumentId(entity.getExpectedRagDocumentId());
        vo.setExpectedRagChunkId(entity.getExpectedRagChunkId());
        vo.setExpectedRagKeywords(entity.getExpectedRagKeywords());
        vo.setExpectedAnswerKeywords(entity.getExpectedAnswerKeywords());
        vo.setMinAnswerScore(entity.getMinAnswerScore());
        vo.setTags(entity.getTags());
        vo.setEnableStatus(entity.getEnableStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
