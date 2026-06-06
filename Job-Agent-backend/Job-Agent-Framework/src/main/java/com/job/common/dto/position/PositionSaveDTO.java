package com.job.common.dto.position;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:岗位新增和编辑请求参数，后台岗位表单提交时使用
 * 日期:2026/6/6 15:20
 */
@Data
public class PositionSaveDTO {

    /**
     * 公司ID。
     * P表示参数描述，岗位必须挂到一个公司下面，形成公司和岗位的一对多关系。
     */
    @NotNull(message = "公司ID不能为空")
    private Long companyId;

    /**
     * 岗位名称。
     */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 255, message = "岗位名称长度不能超过255位")
    private String jobTitle;

    /**
     * 岗位类别。
     */
    @Size(max = 128, message = "岗位类别长度不能超过128位")
    private String jobCategory;

    /**
     * 工作城市。
     */
    @Size(max = 64, message = "城市长度不能超过64位")
    private String city;

    /**
     * 工作区域。
     */
    @Size(max = 64, message = "区域长度不能超过64位")
    private String district;

    /**
     * 最低薪资，单位：元/月。
     */
    @Min(value = 0, message = "最低薪资不能小于0")
    private Integer minSalary;

    /**
     * 最高薪资，单位：元/月。
     */
    @Min(value = 0, message = "最高薪资不能小于0")
    private Integer maxSalary;

    /**
     * 薪资月份，例如 12、13、14。
     */
    @Min(value = 1, message = "薪资月份不能小于1")
    @Max(value = 36, message = "薪资月份不能大于36")
    private Integer salaryMonths;

    /**
     * 学历要求。
     */
    @Size(max = 64, message = "学历要求长度不能超过64位")
    private String educationReq;

    /**
     * 经验要求。
     */
    @Size(max = 64, message = "经验要求长度不能超过64位")
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
    @Size(max = 512, message = "技能关键词长度不能超过512位")
    private String skillKeywords;

    /**
     * 工作类型：全职/实习/远程。
     */
    @Size(max = 64, message = "工作类型长度不能超过64位")
    private String workType;

    /**
     * 福利标签，逗号分隔。
     */
    @Size(max = 512, message = "福利标签长度不能超过512位")
    private String welfareTags;

    /**
     * 岗位来源。
     */
    @Size(max = 64, message = "岗位来源长度不能超过64位")
    private String source;

    /**
     * 岗位来源链接。
     */
    @Size(max = 512, message = "岗位来源链接长度不能超过512位")
    private String sourceUrl;

    /**
     * 岗位状态：0 草稿/下线，1 已发布。
     */
    private Integer status;
}
