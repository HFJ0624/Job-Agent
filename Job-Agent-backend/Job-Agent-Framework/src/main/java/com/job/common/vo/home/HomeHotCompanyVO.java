package com.job.common.vo.home;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.job.common.entity.company.JobCompany;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:首页热门公司展示对象，按公司正在招聘的已发布岗位数排序
 * 日期:2026/6/24
 */
@Data
public class HomeHotCompanyVO {

    /**
     * 公司ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 公司 Logo。
     */
    private String logoUrl;

    /**
     * 所属行业。
     */
    private String industry;

    /**
     * 公司规模。
     */
    private String companySize;

    /**
     * 融资阶段。
     */
    private String financingStage;

    /**
     * 正在招聘的已发布岗位数。
     */
    private Long jobCount;

    /**
     * 将公司实体和岗位数合并成首页展示对象。
     *
     * @param company 公司实体
     * @param jobCount 已发布岗位数
     * @return 首页热门公司对象
     */
    public static HomeHotCompanyVO from(JobCompany company, Long jobCount) {
        HomeHotCompanyVO vo = new HomeHotCompanyVO();
        vo.setId(company.getId());
        vo.setCompanyName(company.getCompanyName());
        vo.setLogoUrl(company.getLogoUrl());
        vo.setIndustry(company.getIndustry());
        vo.setCompanySize(company.getCompanySize());
        vo.setFinancingStage(company.getFinancingStage());
        vo.setJobCount(jobCount == null ? 0L : jobCount);
        return vo;
    }
}
