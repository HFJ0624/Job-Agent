package com.job.common.dto.greeting;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:生成 HR 打招呼语请求参数
 * 日期: 2026/6/8 13:57
 */
@Data
public class GreetingGenerateRequestDTO {

    /**
     * 简历ID。
     * 说明：用户可以选择不同简历生成不同话术。
     */
    @NotNull(message = "简历ID不能为空")
    private Long resumeId;

    /**
     * 语气风格。
     * 允许值建议：正式、自然、自信、实习生风格、社招风格、简洁直达。
     */
    @Size(max = 32, message = "语气风格不能超过32位")
    private String style;
}
