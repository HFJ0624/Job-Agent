package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.FrontHomeService;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeScoreService;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.home.HomeHotCompanyVO;
import com.job.common.vo.home.HomeOverviewVO;
import com.job.common.vo.home.HomeResumeMatchReportVO;
import com.job.common.vo.position.PositionVO;
import com.job.common.vo.resume.ResumeScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:用户端首页聚合服务实现，统一从数据库读取首页需要的真实数据
 * 日期:2026/6/24
 */
@Service
@RequiredArgsConstructor
public class FrontHomeServiceImpl implements FrontHomeService {

    /**
     * 已发布岗位状态。
     */
    private static final int POSITION_STATUS_PUBLISHED = 1;

    /**
     * 正常公司状态。
     */
    private static final int COMPANY_STATUS_NORMAL = 1;

    /**
     * 逻辑未删除标记。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 首页推荐岗位数量。
     */
    private static final long RECOMMENDED_JOB_LIMIT = 3L;

    /**
     * 首页热门公司数量。
     */
    private static final int HOT_COMPANY_LIMIT = 5;

    private final JobPositionService jobPositionService;
    private final JobCompanyService jobCompanyService;
    private final JobResumeService jobResumeService;
    private final JobResumeScoreService jobResumeScoreService;

    /**
     * 查询用户端首页聚合数据。
     *
     * 方法步骤:
     * 1. 查询最新已发布岗位，作为首页推荐岗位。
     * 2. 按已发布岗位数量统计热门公司。
     * 3. 查询当前用户默认/最新简历和最新评分，生成简历报告。
     * 4. 根据真实数据状态生成首页 AI 建议，不编造不存在的评分或匹配度。
     *
     * @param userId 当前登录用户ID
     * @return 首页聚合数据
     */
    @Override
    public HomeOverviewVO getOverview(Long userId) {
        HomeOverviewVO overview = new HomeOverviewVO();
        List<PositionVO> recommendedJobs = listRecommendedJobs();
        List<HomeHotCompanyVO> hotCompanies = listHotCompanies();
        HomeResumeMatchReportVO report = buildResumeReport(userId);

        overview.setRecommendedJobs(recommendedJobs);
        overview.setHotCompanies(hotCompanies);
        overview.setResumeMatchReport(report);
        overview.setAiSuggestion(buildAiSuggestion(report, recommendedJobs));
        return overview;
    }

    /**
     * 查询首页推荐岗位。
     *
     * @return 最新发布的岗位展示对象
     */
    private List<PositionVO> listRecommendedJobs() {
        IPage<JobPosition> positionPage = jobPositionService.pagePositions(
                1L,
                RECOMMENDED_JOB_LIMIT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        return convertToPositionVOList(positionPage.getRecords());
    }

    /**
     * 查询热门公司。
     *
     * 方法步骤:
     * 1. 先从 job_position 按 company_id 分组，统计每家公司已发布岗位数。
     * 2. 再批量查询 company 表，过滤已删除或禁用公司。
     * 3. 最后按岗位数倒序返回，保证热门公司来自真实招聘数据。
     *
     * @return 热门公司列表
     */
    private List<HomeHotCompanyVO> listHotCompanies() {
        List<Map<String, Object>> rows = jobPositionService.listMaps(new QueryWrapper<JobPosition>()
                .select("company_id AS companyId", "COUNT(1) AS jobCount")
                .eq("is_deleted", NOT_DELETED)
                .eq("status", POSITION_STATUS_PUBLISHED)
                .groupBy("company_id")
                .orderByDesc("jobCount")
                .last("limit " + HOT_COMPANY_LIMIT));

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Long> jobCountMap = rows.stream()
                .collect(Collectors.toMap(
                        row -> toLong(firstPresent(row, "companyId", "company_id")),
                        row -> toLong(firstPresent(row, "jobCount", "job_count", "COUNT(1)")),
                        (left, right) -> left
                ));

        if (jobCountMap.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, JobCompany> companyMap = jobCompanyService.list(new LambdaQueryWrapper<JobCompany>()
                        .in(JobCompany::getId, jobCountMap.keySet())
                        .eq(JobCompany::getStatus, COMPANY_STATUS_NORMAL)
                        .eq(JobCompany::getIsDeleted, NOT_DELETED))
                .stream()
                .collect(Collectors.toMap(JobCompany::getId, Function.identity(), (left, right) -> left));

        return jobCountMap.entrySet()
                .stream()
                .filter(entry -> companyMap.containsKey(entry.getKey()))
                .map(entry -> HomeHotCompanyVO.from(companyMap.get(entry.getKey()), entry.getValue()))
                .sorted((left, right) -> Long.compare(right.getJobCount(), left.getJobCount()))
                .limit(HOT_COMPANY_LIMIT)
                .toList();
    }

    /**
     * 构建首页简历报告。
     *
     * @param userId 当前登录用户ID
     * @return 首页报告；没有简历时返回空报告
     */
    private HomeResumeMatchReportVO buildResumeReport(Long userId) {
        List<JobResume> resumes = jobResumeService.listUserResumes(userId);
        if (resumes == null || resumes.isEmpty()) {
            return HomeResumeMatchReportVO.empty();
        }

        // listUserResumes 已经按默认简历、创建时间倒序排序，第一条就是首页最应该展示的简历。
        JobResume resume = resumes.get(0);
        ResumeScoreVO score = jobResumeScoreService.getLatestScore(userId, resume.getId());
        return HomeResumeMatchReportVO.from(resume, score);
    }

    /**
     * 根据真实首页数据生成建议文案。
     *
     * @param report 简历报告
     * @param recommendedJobs 推荐岗位
     * @return 首页建议
     */
    private String buildAiSuggestion(HomeResumeMatchReportVO report, List<PositionVO> recommendedJobs) {
        if (report == null || !Boolean.TRUE.equals(report.getHasResume())) {
            return "你还没有上传简历。先上传一份简历，系统才能基于简历内容给出评分和岗位匹配建议。";
        }

        if (!Boolean.TRUE.equals(report.getHasScore())) {
            return "已找到你的简历，但还没有评分记录。建议先进入简历页面完成 AI 评分，再根据评分结果优化投递方向。";
        }

        if (report.getScore() != null && report.getScore() < 70) {
            return "你的简历评分还有提升空间，建议优先补充项目结果、量化指标和核心技能证据后再集中投递。";
        }

        if (recommendedJobs == null || recommendedJobs.isEmpty()) {
            return "你的简历已完成评分。当前还没有已发布岗位，等后台发布岗位后可以继续做匹配分析。";
        }

        String firstSuggestion = report.getSuggestions() == null || report.getSuggestions().isEmpty()
                ? null
                : report.getSuggestions().get(0);
        if (StringUtils.hasText(firstSuggestion)) {
            return firstSuggestion;
        }
        return "你的简历已完成评分，可以优先查看首页推荐岗位，并对感兴趣的岗位发起匹配分析。";
    }

    /**
     * 把岗位实体批量转换成 PositionVO。
     *
     * @param positions 岗位实体列表
     * @return 前端展示对象列表
     */
    private List<PositionVO> convertToPositionVOList(List<JobPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, JobCompany> companyMap = jobCompanyService.listByIds(
                        positions.stream().map(JobPosition::getCompanyId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(JobCompany::getId, Function.identity(), (left, right) -> left));

        return positions.stream()
                .map(position -> PositionVO.from(position, companyMap.get(position.getCompanyId())))
                .toList();
    }

    /**
     * 从 Map 中按多个 key 依次取值。
     *
     * @param row 查询结果行
     * @param keys 候选字段名
     * @return 第一个非空值
     */
    private Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 将数据库聚合结果转换成 Long。
     *
     * @param value 原始值
     * @return Long 值，空值返回 0
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return bigIntegerValue.longValue();
        }
        if (value instanceof BigDecimal bigDecimalValue) {
            return bigDecimalValue.longValue();
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
