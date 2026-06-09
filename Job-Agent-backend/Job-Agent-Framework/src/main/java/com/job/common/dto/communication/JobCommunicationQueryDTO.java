package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 求职沟通记录分页查询 DTO
 */
@Data
public class JobCommunicationQueryDTO {

    /**
     * 当前页。
     */
    private Long pageNo = 1L;

    /**
     * 每页数量。
     */
    private Long pageSize = 10L;

    /**
     * 状态筛选。
     */
    private String status;

    /**
     * 平台筛选。
     */
    private String platform;

    /**
     * 关键词。
     * 第一版可以用于搜索 HR 名称、备注。
     */
    private String keyword;
}
