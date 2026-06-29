package com.job.common.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台首页求职跟进 Agent 看板项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminFollowUpAgentItemVO {

    /**
     * 指标标题。
     */
    private String title;

    /**
     * 指标数量。
     */
    private Long value;

    /**
     * 辅助说明。
     */
    private String description;

    /**
     * 展示级别：success / warning / danger / info。
     */
    private String level;
}
