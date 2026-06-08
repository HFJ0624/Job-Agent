package com.job.common.dto.preference;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:智能岗位推荐查询参数
 */
@Data
public class JobRecommendQueryDTO {

    /**
     * 推荐数量，默认10。
     */
    private Integer limit = 10;

    /**
     * 可选关键词。
     * 如果用户临时输入 Java、后端、AI应用开发，可以叠加到推荐条件里。
     */
    private String keyword;

    /**
     * 可选城市。
     * 如果传入，则优先使用这里的城市覆盖用户偏好城市。
     */
    private String city;
}
