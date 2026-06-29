package com.job.common.dto.interview;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 提交学习计划复测答案请求。
 */
@Data
public class MockInterviewStudyPlanRetestSubmitDTO {

    /**
     * 用户复测回答。
     */
    @NotBlank(message = "复测回答不能为空")
    private String userAnswer;
}
