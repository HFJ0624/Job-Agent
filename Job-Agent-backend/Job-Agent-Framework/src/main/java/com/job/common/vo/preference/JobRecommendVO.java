package com.job.common.vo.preference;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 作者:hfj
 * 功能:岗位推荐结果 VO
 */
@Data
public class JobRecommendVO {

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 岗位名称。
     */
    private String jobTitle;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 城市。
     */
    private String city;

    /**
     * 区域。
     */
    private String district;

    /**
     * 最低薪资。
     */
    private Integer minSalary;

    /**
     * 最高薪资。
     */
    private Integer maxSalary;

    /**
     * 学历要求。
     */
    private String educationReq;

    /**
     * 经验要求。
     */
    private String experienceReq;

    /**
     * 技能关键词。
     */
    private String skillKeywords;

    /**
     * 推荐分。
     */
    private BigDecimal recommendScore;

    /**
     * 推荐等级。
     */
    private String recommendLevel;

    /**
     * 命中技能。
     */
    private List<String> matchedSkills;

    /**
     * 缺失技能。
     */
    private List<String> missingSkills;

    /**
     * 推荐理由。
     */
    private List<String> reasons;
}
