package com.job.common.entity.application;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:求职投递记录实体
 * 说明:
 * 1. 用户对某个岗位产生求职动作后，会生成一条投递记录。
 * 2. 记录状态从“感兴趣”到“已沟通、已投递、面试中、Offer”等流转。
 * 3. companyName、jobTitle、city、salaryText 是快照字段，避免岗位信息变化后历史记录展示异常。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_application_record")
public class JobApplicationRecord extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 投递使用的简历ID。
     */
    private Long resumeId;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 公司名称快照。
     */
    private String companyName;

    /**
     * 岗位名称快照。
     */
    private String jobTitle;

    /**
     * 城市快照。
     */
    private String city;

    /**
     * 薪资快照。
     */
    private String salaryText;

    /**
     * 来源。
     */
    private String source;

    /**
     * 求职状态。
     */
    private String status;

    /**
     * 优先级：LOW / NORMAL / HIGH。
     */
    private String priority;

    /**
     * HR名称。
     */
    private String hrName;

    /**
     * HR联系方式。
     */
    private String hrContact;

    /**
     * 投递时间。
     */
    private Date applyTime;

    /**
     * 面试时间。
     */
    private Date interviewTime;

    /**
     * 下次跟进时间。
     */
    private Date nextFollowTime;

    /**
     * 备注。
     */
    private String note;

    /**
     * 最近一次动作说明。
     */
    private String lastAction;
}
