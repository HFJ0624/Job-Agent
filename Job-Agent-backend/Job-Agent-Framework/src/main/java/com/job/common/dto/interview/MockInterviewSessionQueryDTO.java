package com.job.common.dto.interview;

import lombok.Data;

/**
 * 功能: 后台模拟面试会话查询参数。
 */
@Data
public class MockInterviewSessionQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    private String status;

    private String keyword;
}
