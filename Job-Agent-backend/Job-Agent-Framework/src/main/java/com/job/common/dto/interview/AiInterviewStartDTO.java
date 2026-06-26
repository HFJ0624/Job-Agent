package com.job.common.dto.interview;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 功能: AI 语音面试启动参数。
 *
 * 说明:
 * 1. 和旧的 mockInterview start 不同，这里不要求用户先有求职记录。
 * 2. 用户直接选择自己的简历和目标岗位，后端校验归属后生成面试题。
 */
@Data
public class AiInterviewStartDTO {

    /**
     * 用户选择的简历ID。
     */
    @NotNull(message = "简历ID不能为空")
    private Long resumeId;

    /**
     * 用户选择的岗位ID。
     */
    @NotNull(message = "岗位ID不能为空")
    private Long jobId;

    /**
     * 题目数量，默认 6 道，后端会限制范围。
     */
    private Integer questionCount = 6;
}
