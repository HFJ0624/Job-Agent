package com.job.common.vo.home;

import com.job.common.vo.position.PositionVO;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 作者:hfj
 * 功能:用户端首页聚合数据，统一承载推荐岗位、热门公司、简历报告和 AI 建议
 * 日期:2026/6/24
 */
@Data
public class HomeOverviewVO {

    /**
     * 首页推荐岗位。
     * 说明: 直接复用 PositionVO，避免首页和岗位列表出现两套字段。
     */
    private List<PositionVO> recommendedJobs = Collections.emptyList();

    /**
     * 热门公司列表。
     */
    private List<HomeHotCompanyVO> hotCompanies = Collections.emptyList();

    /**
     * 当前用户的简历匹配/评分报告。
     */
    private HomeResumeMatchReportVO resumeMatchReport;

    /**
     * 首页右侧 AI 求职建议。
     */
    private String aiSuggestion;
}
