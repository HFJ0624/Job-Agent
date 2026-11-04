package com.job.bootstrap.agent.scenario.jobsearch;

import com.job.common.vo.preference.UserJobPreferenceVO;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: 搜索约束快照（第一阶段）。
 *
 * 设计说明（对应文档“工具与条件约束”）:
 * 1. **本轮显式条件优先于历史偏好**；每个字段记录来源（EXPLICIT/PREFERENCE），
 *    便于解释“为什么查了上海”和审计；
 * 2. 硬条件（城市/薪资/学历）**模型不能修改**：执行器查询前用本快照覆盖模型给的值，
 *    结果出来后按业务字段复核；
 * 3. 模型只能调整等价关键词与页码——这两项是策略空间，不是硬条件。
 *
 * @param keyword      搜索关键词（可为空）
 * @param city         城市（可为空；硬条件）
 * @param minSalary    薪资下限（元/月；结果复核用）
 * @param educationReq 学历要求（可为空；硬条件）
 * @param sources      字段来源标记
 */
public record SearchConstraints(
        String keyword,
        String city,
        Integer minSalary,
        String educationReq,
        Map<String, String> sources,
        /**
         * 目标方向（不可变）：来自用户目标或长期偏好，模型调整召回关键词时不能改变它。
         * 结果必须命中目标方向（岗位标题/技能/类别包含），否则不接受。
         */
        String targetRole
) {

    public static final String SOURCE_EXPLICIT = "EXPLICIT";
    public static final String SOURCE_PREFERENCE = "PREFERENCE";

    /** 从本轮显式参数 + 长期偏好合并（显式优先）。 */
    private static SearchConstraints computeBase(Map<String, Object> explicitParams, UserJobPreferenceVO preference) {
        Map<String, String> sources = new LinkedHashMap<>();

        String keyword = explicitText(explicitParams, "keyword");
        if (StringUtils.hasText(keyword)) {
            sources.put("keyword", SOURCE_EXPLICIT);
        }

        String city = explicitText(explicitParams, "city");
        if (!StringUtils.hasText(city) && preference != null && StringUtils.hasText(preference.getExpectedCity())) {
            city = preference.getExpectedCity().trim();
            sources.put("city", SOURCE_PREFERENCE);
        } else if (StringUtils.hasText(city)) {
            sources.put("city", SOURCE_EXPLICIT);
        }

        Integer minSalary = explicitNumber(explicitParams, "minSalary");
        if (minSalary == null && preference != null && preference.getMinSalary() != null) {
            minSalary = preference.getMinSalary();
            sources.put("minSalary", SOURCE_PREFERENCE);
        } else if (minSalary != null) {
            sources.put("minSalary", SOURCE_EXPLICIT);
        }
        // 统一为元/月：小于 1000 视为用户按 K 表达（20 → 20000），大于等于 1000 原样
        minSalary = normalizeSalaryToYuan(minSalary);

        String education = explicitText(explicitParams, "educationReq");
        if (!StringUtils.hasText(education)) {
            education = null; // 学历默认不设硬条件，避免误伤（偏好里的 expectedEducation 是用户自身学历，不是要求）
        } else {
            sources.put("educationReq", SOURCE_EXPLICIT);
        }

        // 目标方向：显式关键词优先，否则取偏好期望岗位；模型不能修改
        String targetRole = StringUtils.hasText(keyword)
                ? keyword
                : (preference != null && StringUtils.hasText(preference.getExpectedJobTitle())
                        ? preference.getExpectedJobTitle().trim()
                        : null);

        return new SearchConstraints(keyword, city, minSalary, education, sources, targetRole);
    }

    /** 兼容旧调用（无历史条件）。 */
    public static SearchConstraints merge(Map<String, Object> explicitParams, UserJobPreferenceVO preference) {
        return computeBase(explicitParams, preference);
    }

    /**
     * 三级合并（任务 R3）：本轮明确更新 > 原任务条件 > 长期偏好。
     *
     * <p>未提及不等于删除——只有本轮显式给出的字段才覆盖；历史条件整体保留，
     * 来源标记为 PREVIOUS 以便审计“这条条件是哪来的”。</p>
     */
    public static SearchConstraints merge(Map<String, Object> explicitParams,
                                          SearchConstraints previous,
                                          UserJobPreferenceVO preference) {
        SearchConstraints base = computeBase(explicitParams, preference);
        if (previous == null) {
            return base;
        }
        Map<String, String> sources = new LinkedHashMap<>(base.sources());

        String keyword = StringUtils.hasText(base.keyword()) ? base.keyword() : previous.keyword();
        if (!StringUtils.hasText(base.keyword()) && StringUtils.hasText(previous.keyword())) {
            sources.put("keyword", "PREVIOUS");
        }
        String city = StringUtils.hasText(base.city()) ? base.city() : previous.city();
        if (!StringUtils.hasText(base.city()) && StringUtils.hasText(previous.city())) {
            sources.put("city", "PREVIOUS");
        }
        Integer minSalary = base.minSalary() != null ? base.minSalary() : previous.minSalary();
        if (base.minSalary() == null && previous.minSalary() != null) {
            sources.put("minSalary", "PREVIOUS");
        }
        String education = StringUtils.hasText(base.educationReq()) ? base.educationReq() : previous.educationReq();
        if (!StringUtils.hasText(base.educationReq()) && StringUtils.hasText(previous.educationReq())) {
            sources.put("educationReq", "PREVIOUS");
        }
        String targetRole = StringUtils.hasText(base.targetRole())
                ? base.targetRole()
                : (StringUtils.hasText(previous.targetRole()) ? previous.targetRole() : keyword);

        return new SearchConstraints(keyword, city, minSalary, education, sources, targetRole);
    }

    // 薪资统一为元/月（<1000 视为 K）
    private static Integer normalizeSalaryToYuan(Integer salary) {
        if (salary == null || salary <= 0) {
            return null;
        }
        return salary < 1000 ? salary * 1000 : salary;
    }

    /** 是否设置了硬条件（城市/薪资/学历）。 */
    public boolean hasHardConstraints() {
        return StringUtils.hasText(city) || minSalary != null || StringUtils.hasText(educationReq);
    }

    /**
     * 硬条件复核（结果侧）：城市不符或薪资上限低于期望 → 不合格。
     *
     * <p>说明: 信息不足以验证某硬条件的岗位按“不合格”处理（文档要求：
     * 不能把无法验证的岗位直接当成满足条件）。</p>
     */
    public boolean accepts(JobCandidates.JobCandidateItem item) {
        if (item == null || item.raw() == null) {
            return false;
        }
        Map<String, Object> raw = item.raw();

        if (StringUtils.hasText(city)) {
            String jobCity = text(raw.get("city"));
            if (!StringUtils.hasText(jobCity) || !jobCity.contains(city)) {
                return false;
            }
        }

        if (minSalary != null) {
            Integer jobMax = number(raw.get("maxSalary"));
            if (jobMax == null || jobMax < minSalary) {
                return false;
            }
        }

        if (StringUtils.hasText(educationReq)) {
            String jobEducation = text(raw.get("educationReq"));
            if (!StringUtils.hasText(jobEducation) || !jobEducation.contains(educationReq)) {
                return false;
            }
        }

        /*
         * 目标方向复核（任务 R4）：模型可以换等价召回词，但不能换业务目标。
         * 岗位标题/技能/类别都必须能对上目标方向，否则不接受。
         */
        if (StringUtils.hasText(targetRole)) {
            String haystack = text(raw.get("jobTitle")) + "|" + text(raw.get("skillKeywords"))
                    + "|" + text(raw.get("jobCategory"));
            if (!haystack.toLowerCase().contains(targetRole.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    private static String explicitText(Map<String, Object> params, String key) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Integer explicitNumber(Map<String, Object> params, String key) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static Integer number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
