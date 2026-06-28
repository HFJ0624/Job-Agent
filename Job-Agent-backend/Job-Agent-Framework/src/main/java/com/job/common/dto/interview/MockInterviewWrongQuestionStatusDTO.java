package com.job.common.dto.interview;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改错题掌握状态请求。
 */
@Data
public class MockInterviewWrongQuestionStatusDTO {

    /**
     * 掌握状态：UNMASTERED / REVIEWING / MASTERED。
     */
    @NotBlank(message = "掌握状态不能为空")
    private String masteryStatus;
}
