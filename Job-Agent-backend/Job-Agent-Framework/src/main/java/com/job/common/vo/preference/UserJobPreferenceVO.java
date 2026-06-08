package com.job.common.vo.preference;

import com.job.common.entity.preference.UserJobPreference;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:用户求职偏好返回 VO
 */
@Data
public class UserJobPreferenceVO {

    private Long id;
    private String expectedJobTitle;
    private String expectedCity;
    private Integer minSalary;
    private Integer maxSalary;
    private String expectedIndustry;
    private String expectedCompanySize;
    private String expectedFinancingStage;
    private String expectedEducation;
    private String expectedExperience;
    private String expectedWorkType;
    private String skillKeywords;
    private String remark;

    /**
     * Entity 转 VO。
     */
    public static UserJobPreferenceVO from(UserJobPreference entity) {
        if (entity == null) {
            return null;
        }

        UserJobPreferenceVO vo = new UserJobPreferenceVO();
        vo.setId(entity.getId());
        vo.setExpectedJobTitle(entity.getExpectedJobTitle());
        vo.setExpectedCity(entity.getExpectedCity());
        vo.setMinSalary(entity.getMinSalary());
        vo.setMaxSalary(entity.getMaxSalary());
        vo.setExpectedIndustry(entity.getExpectedIndustry());
        vo.setExpectedCompanySize(entity.getExpectedCompanySize());
        vo.setExpectedFinancingStage(entity.getExpectedFinancingStage());
        vo.setExpectedEducation(entity.getExpectedEducation());
        vo.setExpectedExperience(entity.getExpectedExperience());
        vo.setExpectedWorkType(entity.getExpectedWorkType());
        vo.setSkillKeywords(entity.getSkillKeywords());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
