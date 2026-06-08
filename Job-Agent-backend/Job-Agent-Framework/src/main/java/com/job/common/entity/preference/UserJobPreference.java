package com.job.common.entity.preference;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:用户求职偏好实体
 * 说明:
 * 1. 每个用户只维护一份求职偏好。
 * 2. 后续岗位推荐、Agent 推荐岗位、岗位匹配都可以复用这份偏好。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_job_preference")
public class UserJobPreference extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 期望岗位，例如 Java 后端开发。
     */
    private String expectedJobTitle;

    /**
     * 期望城市，例如 上海。
     */
    private String expectedCity;

    /**
     * 最低期望薪资，单位元。
     */
    private Integer minSalary;

    /**
     * 最高期望薪资，单位元。
     */
    private Integer maxSalary;

    /**
     * 期望行业。
     */
    private String expectedIndustry;

    /**
     * 期望公司规模。
     */
    private String expectedCompanySize;

    /**
     * 期望融资阶段。
     */
    private String expectedFinancingStage;

    /**
     * 用户学历。
     */
    private String expectedEducation;

    /**
     * 用户经验。
     */
    private String expectedExperience;

    /**
     * 期望工作类型，例如 全职、实习、远程。
     */
    private String expectedWorkType;

    /**
     * 用户技能关键词，逗号分隔。
     */
    private String skillKeywords;

    /**
     * 补充说明。
     */
    private String remark;
}
