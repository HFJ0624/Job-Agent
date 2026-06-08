package com.job.common.vo.application;

import lombok.Data;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:求职进度统计 VO
 */
@Data
public class JobApplicationStatsVO {

    /**
     * 总记录数。
     */
    private Long totalCount;

    /**
     * 各状态数量。
     * key 是状态编码，value 是数量。
     */
    private Map<String, Long> statusCountMap;

    /**
     * 今日需要跟进的数量。
     */
    private Long todayFollowCount;

    /**
     * 面试中数量。
     */
    private Long interviewingCount;
}
