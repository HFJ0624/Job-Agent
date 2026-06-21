package com.job.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI 模型路由保存参数
 * 日期:2026/6/21
 */
@Data
public class AiModelRouteSaveDTO {

    /**
     * 业务场景编码。
     */
    @NotBlank(message = "业务场景不能为空")
    private String sceneCode;

    /**
     * 路由名称。
     */
    @NotBlank(message = "路由名称不能为空")
    private String routeName;

    /**
     * 主模型编码。
     */
    @NotBlank(message = "主模型不能为空")
    private String primaryModelCode;

    /**
     * 备用模型编码，可为空。
     */
    private String fallbackModelCode;

    /**
     * Prompt 编码。
     */
    @NotBlank(message = "Prompt 编码不能为空")
    private String promptCode;

    /**
     * 固定 Prompt 版本 ID；为空时使用当前已发布版本。
     */
    private Long promptVersionId;

    /**
     * 灰度比例，0-100。
     */
    private Integer grayPercent;

    /**
     * A/B 分组，例如 A、B。
     */
    private String abGroup;

    /**
     * 状态。
     */
    private String status;
}
