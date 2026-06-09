package com.job.common.dto.interview;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:开始模拟面试请求参数
 */
@Data
public class MockInterviewStartDTO {

    /**
     * 求职记录ID。
     * 模拟面试必须绑定某条求职记录。
     */
    @NotNull(message = "求职记录ID不能为空")
    private Long applicationId;

    /**
     * 简历ID。
     * 可为空，为空时优先使用求职记录中的 resumeId。
     */
    private Long resumeId;

    /**
     * 题目数量。
     * 默认取 6 道，最多不超过 12 道。
     */
    private Integer questionCount = 6;
}
