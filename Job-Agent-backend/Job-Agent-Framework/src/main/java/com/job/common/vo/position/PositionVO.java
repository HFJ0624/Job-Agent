package com.job.common.vo.position;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:岗位信息响应对象，返回给后台岗位列表和前台岗位搜索页面展示
 * 日期:2026/6/6 15:20
 */
@Data
public class PositionVO {

    /**
     * 岗位ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 公司 Logo 地址。
     */
    private String companyLogoUrl;

    /**
     * 公司所属行业。
     */
    private String companyIndustry;

    /**
     * 公司规模。
     */
    private String companySize;

    /**
     * 公司融资阶段。
     */
    private String financingStage;

    /**
     * 岗位名称。
     */
    private String jobTitle;

    /**
     * 岗位类别。
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
     * 薪资月份。
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
     * 岗位描述。
     */
    private String jobDescription;

    /**
     * 岗位要求。
     */
    private String jobRequirement;

    /**
     * 技能关键词，逗号分隔。
     */
    private String skillKeywords;

    /**
     * 工作类型。
     */
    private String workType;

    /**
     * 福利标签，逗号分隔。
     */
    private String welfareTags;

    /**
     * 岗位来源。
     */
    private String source;

    /**
     * 岗位来源链接。
     */
    private String sourceUrl;

    /**
     * 岗位状态：0 草稿/下线，1 已发布。
     */
    private Integer status;

    /**
     * 发布时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date publishTime;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * 将岗位实体和公司实体合并成前端展示对象。
     * P表示参数描述，company 允许为空，避免公司被误删或数据缺失时岗位列表接口直接失败。
     *
     * @param position 岗位实体
     * @param company 公司实体
     * @return 返回岗位响应对象
     */
    public static PositionVO from(JobPosition position, JobCompany company) {
        PositionVO response = new PositionVO();
        response.setId(position.getId());
        response.setCompanyId(position.getCompanyId());
        response.setJobTitle(position.getJobTitle());
        response.setJobCategory(position.getJobCategory());
        response.setCity(position.getCity());
        response.setDistrict(position.getDistrict());
        response.setMinSalary(position.getMinSalary());
        response.setMaxSalary(position.getMaxSalary());
        response.setSalaryMonths(position.getSalaryMonths());
        response.setEducationReq(position.getEducationReq());
        response.setExperienceReq(position.getExperienceReq());
        response.setJobDescription(position.getJobDescription());
        response.setJobRequirement(position.getJobRequirement());
        response.setSkillKeywords(position.getSkillKeywords());
        response.setWorkType(position.getWorkType());
        response.setWelfareTags(position.getWelfareTags());
        response.setSource(position.getSource());
        response.setSourceUrl(position.getSourceUrl());
        response.setStatus(position.getStatus());
        response.setPublishTime(position.getPublishTime());
        response.setCreateTime(position.getCreateTime());
        response.setUpdateTime(position.getUpdateTime());

        if (company != null) {
            response.setCompanyName(company.getCompanyName());
            response.setCompanyLogoUrl(company.getLogoUrl());
            response.setCompanyIndustry(company.getIndustry());
            response.setCompanySize(company.getCompanySize());
            response.setFinancingStage(company.getFinancingStage());
        }
        return response;
    }
}
