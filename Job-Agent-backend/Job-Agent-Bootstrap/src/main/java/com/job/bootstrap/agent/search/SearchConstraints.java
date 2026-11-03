package com.job.bootstrap.agent.search;

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
 * @param minSalary    薪资下限 K（可为空；结果复核用）
 * @param educationReq 学历要求（可为空；硬条件）
 * @param sources      字段来源标记
 */
public record SearchConstraints(
        String keyword,
        String city,
        Integer minSalary,
        String educationReq,
        Map<String, String> sources
) {

    public static final String SOURCE_EXPLICIT = "EXPLICIT";
    public static final String SOURCE_PREFERENCE = "PREFERENCE";

    /** 从本轮显式参数 + 长期偏好合并（显式优先）。 */
    public static SearchConstraints merge(Map<String, Object> explicitParams, UserJobPreferenceVO preference) {
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

        String education = explicitText(explicitParams, "educationReq");
        if (!StringUtils.hasText(education)) {
            education = null; // 学历默认不设硬条件，避免误伤（偏好里的 expectedEducation 是用户自身学历，不是要求）
        } else {
            sources.put("educationReq", SOURCE_EXPLICIT);
        }

        return new SearchConstraints(keyword, city, minSalary, education, sources);
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
