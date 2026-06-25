package com.job.common.dto.agent;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:Agent Eval 用例保存参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalCaseSaveDTO {
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
}
