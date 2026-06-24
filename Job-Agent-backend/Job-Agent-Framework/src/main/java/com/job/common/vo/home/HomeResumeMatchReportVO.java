package com.job.common.vo.home;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.resume.ResumeScoreVO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 作者:hfj
 * 功能:首页简历报告展示对象，展示当前用户默认/最新简历的真实评分摘要
 * 日期:2026/6/24
 */
@Data
public class HomeResumeMatchReportVO {

    /**
     * 是否已经上传简历。
     */
    private Boolean hasResume = false;

    /**
     * 简历ID。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resumeId;

    /**
     * 简历名称。
     */
    private String resumeName;

    /**
     * 简历解析状态。
     */
    private String resumeStatus;

    /**
     * 是否已经有评分记录。
     */
    private Boolean hasScore = false;

    /**
     * 简历真实评分，来自 resume_score_record。
     */
    private Integer score;

    /**
     * 评分等级。
     */
    private String level;

    /**
     * 总结性评价。
     */
    private String summary;

    /**
     * 首页展示的前三条优化建议。
     */
    private List<String> suggestions = Collections.emptyList();

    /**
     * 没有简历时构造空报告，前端据此展示引导文案。
     *
     * @return 空报告
     */
    public static HomeResumeMatchReportVO empty() {
        return new HomeResumeMatchReportVO();
    }

    /**
     * 根据简历和最新评分构造首页报告。
     *
     * @param resume 当前用户默认或最新简历
     * @param scoreVO 最新评分，可为空
     * @return 首页简历报告
     */
    public static HomeResumeMatchReportVO from(JobResume resume, ResumeScoreVO scoreVO) {
        HomeResumeMatchReportVO vo = new HomeResumeMatchReportVO();
        vo.setHasResume(true);
        vo.setResumeId(resume.getId());
        vo.setResumeName(resume.getResumeName());
        vo.setResumeStatus(resume.getStatus());

        if (scoreVO != null) {
            vo.setHasScore(true);
            vo.setScore(scoreVO.getOverallScore());
            vo.setLevel(scoreVO.getLevel());
            vo.setSummary(scoreVO.getSummary());
            vo.setSuggestions(firstThree(scoreVO.getImprovementSuggestions()));
        }
        return vo;
    }

    /**
     * 首页只展示少量建议，避免卡片内容过长。
     *
     * @param suggestions 全量建议
     * @return 最多三条建议
     */
    private static List<String> firstThree(List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) {
            return Collections.emptyList();
        }
        return suggestions.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(3)
                .toList();
    }
}
