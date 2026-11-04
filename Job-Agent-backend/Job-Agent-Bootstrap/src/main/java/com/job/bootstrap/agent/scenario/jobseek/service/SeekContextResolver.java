package com.job.bootstrap.agent.scenario.jobseek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.scenario.jobsearch.SearchConstraints;
import com.job.bootstrap.agent.scenario.resume.ResumeSelectionService;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.preference.UserJobPreferenceVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 功能: 复合任务的"准备阶段"解析（从 JobSeekWorkflow 抽出）。
 *
 * <p>职责边界：把请求 + 历史上下文变成**有类型的有效上下文**——</p>
 * <ol>
 *   <li>条件：历史快照（两种格式）与本轮显式参数合并，偏好兜底；</li>
 *   <li>简历：显式 resumeId/名称 > 上一轮已选 > 唯一默认 > 唯一简历，否则返回追问；</li>
 *   <li>准备：未解析的简历在此触发解析（写操作放在准备阶段，不伪装成只读）。</li>
 * </ol>
 * <p>不做编排、不调模型，因此可被其他入口复用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeekContextResolver {

    private final AgentPlanParameterExtractor parameterExtractor;
    private final ResumeSelectionService resumeSelectionService;
    private final JobResumeService jobResumeService;
    private final UserJobPreferenceService userJobPreferenceService;
    private final ObjectMapper objectMapper;

    /** 简历解析结果（question 非空表示需要用户补充）。 */
    public record ResumeContext(JobResume resume, String question, List<String> missingFields,
                                Map<String, Object> explicitParams, String targetRoleHint,
                                boolean explicitKeyword, boolean explicitCity) {
    }

    /** 合并本轮有效搜索条件：历史上下文 + 本轮显式 + 长期偏好。 */
    public SearchConstraints mergeConstraints(Long userId, String message, String constraintsJson) {
        Map<String, Object> explicit = parameterExtractor.extract(message);
        SearchConstraints previous = readPreviousConstraints(constraintsJson);

        UserJobPreferenceVO preference = null;
        try {
            preference = userJobPreferenceService.getPreference(userId);
        } catch (Exception exception) {
            log.debug("偏好加载失败，按无偏好处理，error={}", exception.getMessage());
        }
        return SearchConstraints.merge(explicit, previous, preference);
    }

    /** 解析要使用的简历；无法确定时返回追问（不抛异常，交给父任务暂停）。 */
    public ResumeContext resolveResume(Long userId, String message, String constraintsJson,
                                       Long previousResumeId) {
        Map<String, Object> explicit = parameterExtractor.extract(message);
        String targetRoleHint = text(explicit.get("keyword"));

        // 补参没指定简历时，沿用任务上下文里已选的简历（校验归属由 selectionService 负责）
        Long effectivePrevious = previousResumeId != null ? previousResumeId : contextResumeId(constraintsJson);
        ResumeSelectionService.Selection selection =
                resumeSelectionService.select(userId, explicit, effectivePrevious, "做匹配分析");
        if (selection.needsInput()) {
            return new ResumeContext(null, selection.question(), selection.missingFields(),
                    explicit, targetRoleHint, false, false);
        }
        return new ResumeContext(selection.resume(), null, List.of(), explicit, targetRoleHint,
                StringUtils.hasText(targetRoleHint), StringUtils.hasText(text(explicit.get("city"))));
    }

    /** 准备阶段允许写：未解析的简历在这里触发解析（不把带写入的动作伪装成只读）。 */
    public void ensureParsed(JobResume resume) {
        if (!StringUtils.hasText(resume.getRawText())) {
            JobResume parsed = jobResumeService.parseResumeText(resume.getUserId(), resume.getId());
            if (parsed != null && StringUtils.hasText(parsed.getRawText())) {
                resume.setRawText(parsed.getRawText());
            } else {
                throw new BizException("简历没有解析文本，无法生成画像");
            }
        }
    }

    // 历史条件兼容：{constraints:{...}} 包装、SearchConstraints JSON、参数 Map 三种都能读回
    private SearchConstraints readPreviousConstraints(String constraintsJson) {
        if (!StringUtils.hasText(constraintsJson)) {
            return null;
        }
        Map<String, Object> wrapper = readJsonMap(constraintsJson);
        if (wrapper != null && wrapper.get("constraints") instanceof Map<?, ?> nested) {
            return SearchConstraints.merge(castMap(nested), null);
        }
        try {
            SearchConstraints parsed = objectMapper.readValue(constraintsJson, SearchConstraints.class);
            if (parsed.city() != null || parsed.keyword() != null || parsed.targetRole() != null
                    || parsed.minSalary() != null) {
                return parsed;
            }
        } catch (Exception ignored) {
            try {
                Map<String, Object> raw = objectMapper.readValue(constraintsJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                return SearchConstraints.merge(raw, null);
            } catch (Exception second) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            return null;
        }
    }

    private Long contextResumeId(String json) {
        Map<String, Object> map = readJsonMap(json);
        if (map == null || !(map.get("resumeId") instanceof Number number)) {
            return null;
        }
        return number.longValue();
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
