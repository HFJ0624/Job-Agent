package com.job.common.dto.interview;

import lombok.Data;

/**
 * 模拟面试错题本分页查询参数。
 */
@Data
public class MockInterviewWrongQuestionQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 掌握状态：UNMASTERED / REVIEWING / MASTERED。
     */
    private String masteryStatus;

    /**
     * 知识点关键词，可匹配题目、知识点、缺失点。
     */
    private String keyword;
}
