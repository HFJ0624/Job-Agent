package com.job.common.dto.interview;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改学习计划每日任务完成状态请求。
 */
@Data
public class MockInterviewStudyPlanItemStatusDTO {

    /**
     * 完成状态：PENDING / DONE。
     */
    @NotBlank(message = "完成状态不能为空")
    private String completionStatus;
}
