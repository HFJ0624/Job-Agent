package com.job.common.dto.communication;

import lombok.Data;

/**
 * HR 回复识别请求。
 *
 * 说明：
 * 1. communicationId / applicationId 通常由 URL 传入，DTO 中保留是为了后续复用。
 * 2. 第一版只强制要求 hrReplyText，避免前端为了识别传过多业务字段。
 */
@Data
public class HrReplyRecognizeDTO {

    /**
     * 沟通记录 ID，可选。
     */
    private Long communicationId;

    /**
     * 求职记录 ID，可选。
     */
    private Long applicationId;

    /**
     * HR 回复原文。
     */
    private String hrReplyText;

    /**
     * 用户补充说明，例如“我想约到下午”。
     */
    private String userNote;
}
