package com.job.bootstrap.agent.domain.resume;

import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.resume.JobResume;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 功能: 简历定位规则（P1 抽取，复合任务与独立分析共用）。
 *
 * <p>原来 JobSeekWorkflow.resolveResume 与 ResumeReactEntry.pickResume 各写一份，
 * 规则逐渐分叉（是否支持"上一轮已选简历"）。统一到这里：</p>
 * <ol>
 *   <li>显式 resumeId（校验归属，非法直接报错）；</li>
 *   <li>上一轮已选简历（补参延续，需在候选列表中且属于当前用户）；</li>
 *   <li>显式简历名称（包含匹配）；</li>
 *   <li>唯一默认简历；</li>
 *   <li>只有一份简历；</li>
 *   <li>否则返回问题（无简历提示上传 / 多份列出名称）。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ResumeSelectionService {

    private final JobResumeService jobResumeService;

    /**
     * 选择结果。
     *
     * @param resume        选中的简历（null 表示需要用户补充）
     * @param question      需要用户补充时的问题（含可选名称）
     * @param missingFields 缺失字段（固定 resume）
     */
    public record Selection(JobResume resume, String question, List<String> missingFields) {

        public boolean needsInput() {
            return resume == null;
        }

        static Selection picked(JobResume resume) {
            return new Selection(resume, null, List.of());
        }

        static Selection needInput(String question) {
            return new Selection(null, question, List.of("resume"));
        }
    }

    /**
     * 按统一规则选择简历。
     *
     * @param userId           当前用户（归属校验）
     * @param explicit         本轮显式参数（resumeId / resumeName）
     * @param previousResumeId 上一轮任务上下文里已选的简历（可空）
     * @param actionLabel      缺失时的追问措辞（如"做匹配分析"/"分析简历"）
     */
    public Selection select(Long userId, Map<String, Object> explicit,
                            Long previousResumeId, String actionLabel) {
        Map<String, Object> params = explicit == null ? Map.of() : explicit;

        // 1. 显式 resumeId：交给服务校验归属（非法会抛业务异常）
        Long resumeId = longOf(params.get("resumeId"));
        if (resumeId != null) {
            return Selection.picked(jobResumeService.getUserResumeRequired(userId, resumeId));
        }

        List<JobResume> resumes = jobResumeService.listUserResumes(userId);
        if (resumes.isEmpty()) {
            return Selection.needInput("你的账号下还没有可用的简历，请先上传一份简历，我再帮你" + actionLabel + "。");
        }

        // 2. 上一轮已选简历（补参不丢选择）
        JobResume picked = null;
        if (previousResumeId != null) {
            picked = resumes.stream()
                    .filter(r -> r.getId().equals(previousResumeId))
                    .findFirst().orElse(null);
        }

        // 3. 显式名称（包含匹配）
        String resumeName = text(params.get("resumeName"));
        if (picked == null && StringUtils.hasText(resumeName)) {
            picked = resumes.stream()
                    .filter(r -> r.getResumeName() != null && r.getResumeName().contains(resumeName))
                    .findFirst().orElse(null);
        }

        // 4/5. 唯一默认 > 唯一简历
        if (picked == null) {
            List<JobResume> defaults = resumes.stream()
                    .filter(r -> r.getIsDefault() != null && r.getIsDefault() == 1).toList();
            if (defaults.size() == 1) {
                picked = defaults.get(0);
            } else if (resumes.size() == 1) {
                picked = resumes.get(0);
            }
        }

        // 6. 多份无法确定：列出名称让用户选，不要求写数据库 ID
        if (picked == null) {
            String names = String.join("、", resumes.stream().map(JobResume::getResumeName).toList());
            return Selection.needInput("你有多份简历，请告诉我要用哪一份：" + names + "。");
        }
        return Selection.picked(picked);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
