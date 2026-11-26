package com.job.bootstrap.service.match;

import com.job.common.entity.position.JobPosition;
import com.job.common.vo.preference.UserJobPreferenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 硬性条件判定器（任务 J5）。
 *
 * 解决什么问题:
 * 1. 原匹配只有“分数”——用户明确要求上海，北京的岗位技能分再高也会被推荐，
 *    用户拿到的是噪音；
 * 2. 本组件把“用户明确要求”与岗位属性做确定性比对，输出违反项清单——
 *    硬性违反的岗位 recommendApply=false、等级标记“硬性条件不符”，
 *    与“可协商偏好”（薪资浮动、公司规模等）区分开。
 *
 * 设计说明:
 * 1. 数据来源优先级：本轮显式参数（用户在对话里明说的）> 长期偏好（user_job_preference）；
 * 2. 无偏好数据时全部通过——不拿猜测当条件，避免误伤；
 * 3. 纯函数、无 IO：判定逻辑可完整单测，也能被评估链路复用。
 */
@Slf4j
@Component
public class HardConditionEvaluator {

    /** 学历等级（用于“期望学历 vs 岗位要求”比较）。 */
    private static final Map<String, Integer> EDUCATION_LEVELS = new LinkedHashMap<>();

    static {
        EDUCATION_LEVELS.put("大专", 1);
        EDUCATION_LEVELS.put("专科", 1);
        EDUCATION_LEVELS.put("本科", 2);
        EDUCATION_LEVELS.put("学士", 2);
        EDUCATION_LEVELS.put("硕士", 3);
        EDUCATION_LEVELS.put("研究生", 3);
        EDUCATION_LEVELS.put("博士", 4);
    }

    /**
     * 判定输入。
     *
     * @param preference     用户长期求职偏好（可为 null）
     * @param explicitParams 本轮显式参数（用户在对话里明说的条件，优先于长期偏好）
     * @param job            候选岗位
     */
    public record HardConditionInput(
            UserJobPreferenceVO preference,
            Map<String, Object> explicitParams,
            JobPosition job
    ) {
    }

    /**
     * 判定结果。
     *
     * @param passed          硬性条件是否全部通过
     * @param violations      违反项清单（人类可读，如 “城市不符：期望上海，岗位在北京”）
     * @param appliedConditions 实际生效的硬性条件名（用于解释“判了什么”）
     */
    public record HardConditionResult(
            boolean passed,
            List<String> violations,
            List<String> appliedConditions
    ) {
    }

    /**
     * 判定岗位是否满足用户硬性条件。
     */
    public HardConditionResult evaluate(HardConditionInput input) {
        if (input == null || input.job() == null) {
            return new HardConditionResult(true, List.of(), List.of());
        }

        JobPosition job = input.job();
        List<String> violations = new ArrayList<>();
        List<String> applied = new ArrayList<>();

        // 1. 城市：本轮显式 > 长期偏好；“远程”岗位不受城市条件约束
        String expectedCity = firstText(input.explicitParams(), "city", input.preference() == null ? null : input.preference().getExpectedCity());
        if (StringUtils.hasText(expectedCity) && !isRemoteJob(job)) {
            applied.add("city");
            if (!cityMatches(expectedCity, job.getCity())) {
                violations.add("城市不符：期望" + expectedCity + "，岗位在" + safe(job.getCity()));
            }
        }

        // 2. 岗位方向：本轮显式 keyword > 长期期望岗位
        String expectedTitle = firstText(input.explicitParams(), "keyword", input.preference() == null ? null : input.preference().getExpectedJobTitle());
        if (StringUtils.hasText(expectedTitle)) {
            applied.add("jobTitle");
            if (!jobTitleMatches(expectedTitle, job)) {
                violations.add("岗位方向不符：期望" + expectedTitle + "，岗位是" + safe(job.getJobTitle()));
            }
        }

        // 3. 薪资下限：本轮显式 minSalary > 长期偏好 minSalary，对比岗位薪资上限
        Integer minSalary = firstNumber(input.explicitParams(), "minSalary", input.preference() == null ? null : input.preference().getMinSalary());
        if (minSalary != null && minSalary > 0) {
            applied.add("minSalary");
            Integer jobMax = job.getMaxSalary();
            if (jobMax != null && jobMax < minSalary) {
                violations.add("薪资不符：期望不低于" + minSalary + "K，岗位上限" + jobMax + "K");
            }
        }

        // 4. 学历：长期偏好期望学历 vs 岗位学历要求（等级比较）
        String expectedEducation = input.preference() == null ? null : input.preference().getExpectedEducation();
        if (StringUtils.hasText(expectedEducation)) {
            applied.add("education");
            if (!educationSatisfies(expectedEducation, job.getEducationReq())) {
                violations.add("学历不符：期望" + expectedEducation + "，岗位要求" + safe(job.getEducationReq()));
            }
        }

        return new HardConditionResult(violations.isEmpty(), violations, applied);
    }

    // 远程办公岗位不受城市条件约束
    private boolean isRemoteJob(JobPosition job) {
        return StringUtils.hasText(job.getCity()) && job.getCity().contains("远程");
    }

    // 城市匹配：期望城市被岗位城市包含（“上海” ⊂ “上海-浦东区”）或岗位城市被期望包含
    private boolean cityMatches(String expectedCity, String jobCity) {
        if (!StringUtils.hasText(jobCity)) {
            return true; // 岗位未标城市：不因此判违反
        }
        return jobCity.contains(expectedCity.trim()) || expectedCity.trim().contains(jobCity.trim());
    }

    // 岗位方向匹配：期望关键词被岗位标题/描述包含
    private boolean jobTitleMatches(String expectedTitle, JobPosition job) {
        String title = safe(job.getJobTitle());
        String description = safe(job.getJobDescription());
        String keyword = expectedTitle.trim();
        return title.contains(keyword) || description.contains(keyword);
    }

    // 学历满足：候选人学历等级 >= 岗位要求等级（要求无法识别时视为满足）
    private boolean educationSatisfies(String expectedEducation, String educationReq) {
        Integer expectedLevel = resolveEducationLevel(expectedEducation);
        Integer requiredLevel = resolveEducationLevel(educationReq);
        if (expectedLevel == null || requiredLevel == null) {
            return true;
        }
        return expectedLevel >= requiredLevel;
    }

    private Integer resolveEducationLevel(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        for (Map.Entry<String, Integer> entry : EDUCATION_LEVELS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String firstText(Map<String, Object> explicitParams, String key, String fallback) {
        if (explicitParams != null) {
            Object value = explicitParams.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return fallback;
    }

    private Integer firstNumber(Map<String, Object> explicitParams, String key, Integer fallback) {
        if (explicitParams != null) {
            Object value = explicitParams.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // 非数字参数忽略，走长期偏好
                }
            }
        }
        return fallback;
    }

    private String safe(String value) {
        return value == null ? "未知" : value;
    }
}
