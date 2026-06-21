package com.job.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI Prompt 版本保存参数
 * 日期:2026/6/21
 */
@Data
public class AiPromptVersionSaveDTO {

    /**
     * 所属 Prompt 模板 ID。
     */
    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    /**
     * 版本号，例如 v1.0.0。
     */
    @NotBlank(message = "版本号不能为空")
    private String versionNo;

    /**
     * 版本标题。
     */
    @NotBlank(message = "版本标题不能为空")
    private String title;

    /**
     * Prompt 正文，支持 {{变量名}} 占位符。
     */
    @NotBlank(message = "Prompt 内容不能为空")
    private String content;

    /**
     * 变量说明 JSON，方便后台可视化查看。
     */
    private String variablesJson;

    /**
     * 状态，DRAFT/PUBLISHED/ARCHIVED。
     */
    private String status;

    /**
     * 灰度比例，0-100。
     */
    private Integer grayPercent;

    /**
     * A/B 分组，例如 A、B。
     */
    private String abGroup;
}
