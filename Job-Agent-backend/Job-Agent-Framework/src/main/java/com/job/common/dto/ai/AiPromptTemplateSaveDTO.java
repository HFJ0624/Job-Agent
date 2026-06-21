package com.job.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI Prompt 模板保存参数
 * 日期:2026/6/21
 */
@Data
public class AiPromptTemplateSaveDTO {

    /**
     * Prompt 编码。
     */
    @NotBlank(message = "Prompt 编码不能为空")
    private String promptCode;

    /**
     * Prompt 名称。
     */
    @NotBlank(message = "Prompt 名称不能为空")
    private String promptName;

    /**
     * 业务场景编码。
     */
    @NotBlank(message = "业务场景不能为空")
    private String sceneCode;

    /**
     * 模板说明。
     */
    private String description;

    /**
     * 状态。
     */
    private String status;
}
