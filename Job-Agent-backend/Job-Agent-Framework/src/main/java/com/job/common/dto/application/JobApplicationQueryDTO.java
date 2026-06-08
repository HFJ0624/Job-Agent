package com.job.common.dto.application;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:求职记录分页查询参数
 */
@Data
public class JobApplicationQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 求职状态。
     */
    private String status;

    /**
     * 关键词。
     * 可以搜索岗位名称、公司名称、备注。
     */
    private String keyword;

    /**
     * 城市。
     */
    private String city;

    /**
     * 优先级。
     */
    private String priority;
}
