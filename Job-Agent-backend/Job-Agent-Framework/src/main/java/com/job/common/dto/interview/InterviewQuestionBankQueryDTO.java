package com.job.common.dto.interview;

import lombok.Data;

/**
 * 后台面试题库分页查询参数。
 */
@Data
public class InterviewQuestionBankQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String keyword;

    private String questionType;

    private String category;

    private String difficulty;

    private String status;

    private String sourceFile;
}
