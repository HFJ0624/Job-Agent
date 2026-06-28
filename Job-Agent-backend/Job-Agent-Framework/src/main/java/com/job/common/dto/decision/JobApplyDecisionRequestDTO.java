package com.job.common.dto.decision;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 功能: AI 投递决策请求。
 */
@Data
public class JobApplyDecisionRequestDTO {

    /**
     * 用户选择用于投递决策的简历 ID。
     */
    @NotNull(message = "简历ID不能为空")
    private Long resumeId;
}
