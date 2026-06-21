package com.job.common.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.ai.AiModelCallLog;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI 模型调用日志展示 VO
 * 日期:2026/6/21
 */
@Data
public class AiModelCallLogVO {

    private Long id;

    private String traceId;

    private Long userId;

    private String sceneCode;

    private String promptCode;

    private Long promptVersionId;

    private String modelCode;

    private Integer fallbackUsed;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal inputCost;

    private BigDecimal outputCost;

    private BigDecimal totalCost;

    private Long costTime;

    private String status;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AiModelCallLogVO from(AiModelCallLog entity) {
        if (entity == null) {
            return null;
        }

        AiModelCallLogVO vo = new AiModelCallLogVO();
        vo.setId(entity.getId());
        vo.setTraceId(entity.getTraceId());
        vo.setUserId(entity.getUserId());
        vo.setSceneCode(entity.getSceneCode());
        vo.setPromptCode(entity.getPromptCode());
        vo.setPromptVersionId(entity.getPromptVersionId());
        vo.setModelCode(entity.getModelCode());
        vo.setFallbackUsed(entity.getFallbackUsed());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setInputCost(entity.getInputCost());
        vo.setOutputCost(entity.getOutputCost());
        vo.setTotalCost(entity.getTotalCost());
        vo.setCostTime(entity.getCostTime());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
