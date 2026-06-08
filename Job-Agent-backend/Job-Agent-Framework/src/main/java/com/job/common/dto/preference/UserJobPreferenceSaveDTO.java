package com.job.common.dto.preference;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:保存用户求职偏好请求参数
 */
@Data
public class UserJobPreferenceSaveDTO {

    @Size(max = 128, message = "期望岗位不能超过128位")
    private String expectedJobTitle;

    @Size(max = 64, message = "期望城市不能超过64位")
    private String expectedCity;

    /**
     * 最低期望薪资，单位元。
     */
    private Integer minSalary;

    /**
     * 最高期望薪资，单位元。
     */
    private Integer maxSalary;

    @Size(max = 128, message = "期望行业不能超过128位")
    private String expectedIndustry;

    @Size(max = 64, message = "期望公司规模不能超过64位")
    private String expectedCompanySize;

    @Size(max = 64, message = "期望融资阶段不能超过64位")
    private String expectedFinancingStage;

    @Size(max = 64, message = "学历不能超过64位")
    private String expectedEducation;

    @Size(max = 64, message = "经验不能超过64位")
    private String expectedExperience;

    @Size(max = 64, message = "工作类型不能超过64位")
    private String expectedWorkType;

    @Size(max = 512, message = "技能关键词不能超过512位")
    private String skillKeywords;

    @Size(max = 512, message = "补充说明不能超过512位")
    private String remark;
}
