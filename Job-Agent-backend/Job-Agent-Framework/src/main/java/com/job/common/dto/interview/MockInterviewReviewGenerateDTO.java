package com.job.common.dto.interview;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:生成模拟面试复盘报告请求参数
 */
@Data
public class MockInterviewReviewGenerateDTO {

    /**
     * 模拟面试会话ID。
     */
    @NotNull(message = "模拟面试会话ID不能为空")
    private Long sessionId;
}
