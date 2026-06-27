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

    /**
     * 最近抽题去重窗口，单位小时。
     *
     * 说明:
     * 1. 不传时后端默认使用 72 小时。
     * 2. 传 0 表示关闭最近题过滤，适合用户想重复练同一批题的场景。
     * 3. 后端会限制最大值，避免用户传超大窗口导致历史查询范围过大。
     */
    private Integer excludeRecentHours;
}
