package com.job.common.vo.company;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.company.JobCompany;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:公司信息响应对象，返回给管理端公司列表和详情页面展示
 * 日期:2026/6/6 10:30
 */
@Data
public class CompanyVO {

    /**
     * 公司 ID。
     */
    private Long id;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 公司 Logo 地址。
     */
    private String logoUrl;

    /**
     * 行业。
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
     * 公司简介。
     */
    private String description;

    /**
     * 省份。
     */
    private String province;

    /**
     * 城市。
     */
    private String city;

    /**
     * 区县。
     */
    private String district;

    /**
     * 公司地址。
     */
    private String address;

    /**
     * 经度。
     */
    private BigDecimal longitude;

    /**
     * 纬度。
     */
    private BigDecimal latitude;

    /**
     * 发展前景分数。
     */
    private BigDecimal prospectScore;

    /**
     * 公司状态：0 禁用，1 正常。
     */
    private Integer status;

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
     * 将公司实体转换成前端使用的 VO。
     *
     * @param company 数据库公司实体
     * @return 返回给管理端的公司信息
     */
    public static CompanyVO from(JobCompany company) {
        CompanyVO response = new CompanyVO();
        response.setId(company.getId());
        response.setCompanyName(company.getCompanyName());
        response.setLogoUrl(company.getLogoUrl());
        response.setIndustry(company.getIndustry());
        response.setCompanySize(company.getCompanySize());
        response.setFinancingStage(company.getFinancingStage());
        response.setDescription(company.getDescription());
        response.setProvince(company.getProvince());
        response.setCity(company.getCity());
        response.setDistrict(company.getDistrict());
        response.setAddress(company.getAddress());
        response.setLongitude(company.getLongitude());
        response.setLatitude(company.getLatitude());
        response.setProspectScore(company.getProspectScore());
        response.setStatus(company.getStatus());
        response.setCreateTime(company.getCreateTime());
        response.setUpdateTime(company.getUpdateTime());
        return response;
    }
}
