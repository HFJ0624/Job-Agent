package com.job.common.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:提交模拟面试回答请求参数
 */
@Data
public class MockInterviewAnswerDTO {

    /**
     * 题目ID。
     */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /**
     * 用户回答内容。
     */
    @NotBlank(message = "回答内容不能为空")
    private String answerContent;
}
