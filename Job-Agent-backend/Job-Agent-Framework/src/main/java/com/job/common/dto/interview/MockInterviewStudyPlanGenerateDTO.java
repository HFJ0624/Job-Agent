package com.job.common.dto.interview;

import lombok.Data;

/**
 * 生成模拟面试学习计划请求。
 */
@Data
public class MockInterviewStudyPlanGenerateDTO {

    /**
     * 计划天数，第一版支持 3-14 天，默认 7 天。
     */
    private Integer planDays = 7;
}
