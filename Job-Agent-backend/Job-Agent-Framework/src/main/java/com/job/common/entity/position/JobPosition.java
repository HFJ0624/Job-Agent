package com.job.common.entity.position;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:岗位实体类，对应数据库 job_position 表，用来保存公司发布的招聘岗位
 * 日期:2026/6/6 15:20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_position")
public class JobPosition extends BaseEntity {

    /**
     * 公司ID。
     * P表示参数描述，一个公司可以发布多个岗位，一个岗位只属于一个公司。
     */
    private Long companyId;

    /**
     * 岗位名称，例如 Java 后端开发工程师。
     */
    private String jobTitle;

    /**
     * 岗位类别，例如 后端开发、AI应用、产品经理。
     */
    private String jobCategory;

    /**
     * 工作城市。
     */
    private String city;

    /**
     * 工作区域。
     */
    private String district;

    /**
     * 最低薪资，单位：元/月。
     */
    private Integer minSalary;

    /**
     * 最高薪资，单位：元/月。
     */
    private Integer maxSalary;

    /**
     * 薪资月份，例如 12、13、14。
     */
    private Integer salaryMonths;

    /**
     * 学历要求。
     */
    private String educationReq;

    /**
     * 经验要求。
     */
    private String experienceReq;

    /**
     * 岗位描述，通常是工作内容。
     */
    private String jobDescription;

    /**
     * 岗位要求，通常是任职资格。
     */
    private String jobRequirement;

    /**
     * 技能关键词，使用逗号分隔。
     */
    private String skillKeywords;

    /**
     * 工作类型，例如 全职、实习、远程。
     */
    private String workType;

    /**
     * 福利标签，使用逗号分隔。
     */
    private String welfareTags;

    /**
     * 岗位来源：MANUAL/IMPORT/API/CRAWLER。
     */
    private String source;

    /**
     * 岗位来源链接。
     */
    private String sourceUrl;

    /**
     * 岗位状态：0 草稿/下线，1 已发布。
     * P表示参数描述，前台只展示 status=1 的岗位。
     */
    private Integer status;

    /**
     * 发布时间。
     */
    private Date publishTime;
}
