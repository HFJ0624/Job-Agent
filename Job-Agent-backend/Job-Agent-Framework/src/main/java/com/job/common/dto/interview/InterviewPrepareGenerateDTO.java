package com.job.common.dto.interview;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:生成面试准备请求参数
 */
@Data
public class InterviewPrepareGenerateDTO {

    /**
     * 求职记录ID。
     * 说明：面试准备应该绑定某条求职记录，而不是孤立绑定岗位。
     */
    @NotNull(message = "求职记录ID不能为空")
    private Long applicationId;

    /**
     * 简历ID。
     * 说明：如果为空，后端优先使用求职记录中的 resumeId。
     */
    private Long resumeId;
}
