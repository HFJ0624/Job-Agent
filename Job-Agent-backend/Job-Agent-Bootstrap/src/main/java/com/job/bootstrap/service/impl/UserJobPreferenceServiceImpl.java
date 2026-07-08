package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.UserJobPreferenceMapper;
import com.job.bootstrap.service.JobCompanyService;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.dto.preference.JobRecommendQueryDTO;
import com.job.common.dto.preference.UserJobPreferenceSaveDTO;
import com.job.common.entity.company.JobCompany;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.preference.UserJobPreference;
import com.job.common.vo.preference.JobRecommendVO;
import com.job.common.vo.preference.UserJobPreferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 用户求职偏好服务实现。
 *
 * <p>核心职责：管理用户求职偏好的持久化，并基于规则算法为用户推荐匹配岗位，提供可解释的推荐分数和匹配原因。</p>
 *
 * <p>所属业务模块：用户中心模块（user）/ 岗位推荐模块（recommend）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>用户填写求职偏好后，调用 {@link #saveOrUpdatePreference} 保存；</li>
 *   <li>前端首页或推荐页调用 {@link #recommendJobs} 获取匹配岗位列表；</li>
 *   <li>内部按岗位方向、城市、薪资、技能、学历、经验等维度打分排序后返回。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link UserJobPreferenceMapper} 进行偏好数据持久化；</li>
 *   <li>依赖 {@link JobPositionService} 查询候选岗位；</li>
 *   <li>依赖 {@link JobCompanyService} 补全公司信息。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>第一版采用规则推荐，不依赖大模型，保证稳定可解释；</li>
 *   <li>推荐分由岗位方向（15分）、城市（20分）、薪资（15分）、技能（35分）、学历经验（10分）、工作类型（5分）组成，满分100；</li>
 *   <li>候选岗位先按关键词和城市粗筛，再在内存中打分排序，数据量不大时简单稳定；</li>
 *   <li>后续可以加入 Embedding 相似度或 LLM 综合评价，替换 scoreJob 方法即可。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserJobPreferenceServiceImpl implements UserJobPreferenceService {

    private static final int NOT_DELETED = 0;

    private final UserJobPreferenceMapper userJobPreferenceMapper;
    private final JobPositionService jobPositionService;
    private final JobCompanyService jobCompanyService;

    /**
     * 保存或更新求职偏好。
     */
    @Override
    public UserJobPreferenceVO saveOrUpdatePreference(Long userId, UserJobPreferenceSaveDTO dto) {
        UserJobPreference exist = userJobPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserJobPreference>()
                        .eq(UserJobPreference::getUserId, userId)
                        .eq(UserJobPreference::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );

        if (exist == null) {
            exist = new UserJobPreference();
            exist.setUserId(userId);
            exist.setIsDeleted(NOT_DELETED);
        }

        /*
         * 把请求参数写入实体。
         * 这里不直接 BeanUtils.copy，是为了字段变更时更清晰。
         */
        exist.setExpectedJobTitle(trimToNull(dto.getExpectedJobTitle()));
        exist.setExpectedCity(trimToNull(dto.getExpectedCity()));
        exist.setMinSalary(dto.getMinSalary());
        exist.setMaxSalary(dto.getMaxSalary());
        exist.setExpectedIndustry(trimToNull(dto.getExpectedIndustry()));
        exist.setExpectedCompanySize(trimToNull(dto.getExpectedCompanySize()));
        exist.setExpectedFinancingStage(trimToNull(dto.getExpectedFinancingStage()));
        exist.setExpectedEducation(trimToNull(dto.getExpectedEducation()));
        exist.setExpectedExperience(trimToNull(dto.getExpectedExperience()));
        exist.setExpectedWorkType(trimToNull(dto.getExpectedWorkType()));
        exist.setSkillKeywords(trimToNull(dto.getSkillKeywords()));
        exist.setRemark(trimToNull(dto.getRemark()));

        if (exist.getId() == null) {
            userJobPreferenceMapper.insert(exist);
        } else {
            userJobPreferenceMapper.updateById(exist);
        }

        return UserJobPreferenceVO.from(exist);
    }

    /**
     * 查询求职偏好。
     */
    @Override
    public UserJobPreferenceVO getPreference(Long userId) {
        UserJobPreference preference = getPreferenceEntity(userId);
        return UserJobPreferenceVO.from(preference);
    }

    /**
     * 根据求职偏好推荐岗位。
     */
    @Override
    public List<JobRecommendVO> recommendJobs(Long userId, JobRecommendQueryDTO query) {
        UserJobPreference preference = getPreferenceEntity(userId);

        /*
         * 如果用户还没填偏好，给一个空偏好对象，避免空指针。
         * 前端也可以提示用户先填写偏好。
         */
        if (preference == null) {
            preference = new UserJobPreference();
            preference.setUserId(userId);
        }

        /*
         * 第一版先查一批候选岗位，再在内存中打分排序。
         * 数据量不大时这样最简单、稳定。
         */
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<>();

        String keyword = StringUtils.hasText(query.getKeyword())
                ? query.getKeyword().trim()
                : preference.getExpectedJobTitle();

        String city = StringUtils.hasText(query.getCity())
                ? query.getCity().trim()
                : preference.getExpectedCity();

        /*
         * 岗位关键词粗筛。
         */
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(JobPosition::getJobTitle, keyword)
                    .or()
                    .like(JobPosition::getJobDescription, keyword)
                    .or()
                    .like(JobPosition::getJobRequirement, keyword)
                    .or()
                    .like(JobPosition::getSkillKeywords, keyword)
            );
        }

        /*
         * 城市粗筛。
         */
        if (StringUtils.hasText(city)) {
            wrapper.eq(JobPosition::getCity, city);
        }

        /*
         * 只取最近一批岗位做打分。
         * 如果你的岗位表有 status 字段，可以在这里加上已发布状态筛选。
         */
        wrapper.orderByDesc(JobPosition::getPublishTime)
                .last("limit 100");

        List<JobPosition> candidates = jobPositionService.list(wrapper);

        int limit = query.getLimit() == null || query.getLimit() <= 0 ? 10 : query.getLimit();
        limit = Math.min(limit, 30);

        UserJobPreference finalPreference = preference;

        return candidates.stream()
                .map(job -> scoreJob(job, finalPreference, keyword, city))
                .sorted(Comparator.comparing(JobRecommendVO::getRecommendScore).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * 查询用户偏好实体。
     */
    private UserJobPreference getPreferenceEntity(Long userId) {
        return userJobPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserJobPreference>()
                        .eq(UserJobPreference::getUserId, userId)
                        .last("limit 1")
        );
    }

    /**
     * 对单个岗位进行推荐打分。
     *
     * 分数设计:
     * 1. 岗位方向 15分
     * 2. 城市 20分
     * 3. 薪资 15分
     * 4. 技能 35分
     * 5. 学历经验 10分
     * 6. 工作类型 5分
     */
    private JobRecommendVO scoreJob(
            JobPosition job,
            UserJobPreference preference,
            String keyword,
            String city
    ) {
        double score = 0;
        List<String> reasons = new ArrayList<>();
        JobCompany company = jobCompanyService.getCompanyRequired(job.getCompanyId());

        /*
         * 1. 岗位方向匹配。
         */
        if (StringUtils.hasText(keyword) && containsIgnoreCase(job.getJobTitle(), keyword)) {
            score += 15;
            reasons.add("岗位名称与期望方向匹配");
        }

        /*
         * 2. 城市匹配。
         */
        if (StringUtils.hasText(city) && Objects.equals(job.getCity(), city)) {
            score += 20;
            reasons.add("工作城市符合期望");
        } else if (!StringUtils.hasText(city)) {
            score += 10;
            reasons.add("未限制城市，保留该岗位作为候选");
        }

        /*
         * 3. 薪资匹配。
         */
        double salaryScore = calculateSalaryScore(job, preference);
        score += salaryScore;
        if (salaryScore >= 10) {
            reasons.add("薪资范围与期望较匹配");
        }

        /*
         * 4. 技能匹配。
         */
        SkillScore skillScore = calculateSkillScore(job, preference);
        score += skillScore.score();
        if (!skillScore.matchedSkills().isEmpty()) {
            reasons.add("命中技能：" + String.join("、", skillScore.matchedSkills()));
        }

        /*
         * 5. 学历经验匹配。
         */
        double conditionScore = calculateConditionScore(job, preference);
        score += conditionScore;
        if (conditionScore >= 8) {
            reasons.add("学历和经验要求整体匹配");
        }

        /*
         * 6. 工作类型匹配。
         */
        if (StringUtils.hasText(preference.getExpectedWorkType())
                && Objects.equals(job.getWorkType(), preference.getExpectedWorkType())) {
            score += 5;
            reasons.add("工作类型符合期望");
        }

        BigDecimal finalScore = BigDecimal.valueOf(Math.min(score, 100))
                .setScale(2, RoundingMode.HALF_UP);

        JobRecommendVO vo = new JobRecommendVO();
        vo.setJobId(job.getId());
        vo.setJobTitle(job.getJobTitle());
        vo.setCompanyId(job.getCompanyId());

        vo.setCompanyName(company.getCompanyName());

        vo.setCity(job.getCity());
        vo.setDistrict(job.getDistrict());
        vo.setMinSalary(job.getMinSalary());
        vo.setMaxSalary(job.getMaxSalary());
        vo.setEducationReq(job.getEducationReq());
        vo.setExperienceReq(job.getExperienceReq());
        vo.setSkillKeywords(job.getSkillKeywords());
        vo.setRecommendScore(finalScore);
        vo.setRecommendLevel(resolveRecommendLevel(finalScore));
        vo.setMatchedSkills(skillScore.matchedSkills());
        vo.setMissingSkills(skillScore.missingSkills());
        vo.setReasons(reasons.isEmpty() ? List.of("该岗位与当前求职偏好存在一定相关性") : reasons);
        return vo;
    }

    /**
     * 计算薪资匹配分，满分15。
     */
    private double calculateSalaryScore(JobPosition job, UserJobPreference preference) {
        Integer expectedMin = preference.getMinSalary();
        Integer expectedMax = preference.getMaxSalary();

        if (expectedMin == null && expectedMax == null) {
            return 8;
        }

        Integer jobMin = job.getMinSalary();
        Integer jobMax = job.getMaxSalary();

        if (jobMin == null && jobMax == null) {
            return 5;
        }

        int safeJobMax = jobMax == null ? jobMin : jobMax;
        int safeJobMin = jobMin == null ? jobMax : jobMin;

        if (expectedMin != null && safeJobMax < expectedMin) {
            return 2;
        }

        if (expectedMax != null && safeJobMin > expectedMax) {
            return 8;
        }

        return 15;
    }

    /**
     * 计算技能匹配分，满分35。
     */
    private SkillScore calculateSkillScore(JobPosition job, UserJobPreference preference) {
        List<String> userSkills = splitKeywords(preference.getSkillKeywords());
        List<String> jobSkills = splitKeywords(job.getSkillKeywords());

        if (userSkills.isEmpty() || jobSkills.isEmpty()) {
            return new SkillScore(10, Collections.emptyList(), jobSkills);
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String jobSkill : jobSkills) {
            boolean hit = userSkills.stream()
                    .anyMatch(userSkill -> equalsIgnoreCase(userSkill, jobSkill));

            if (hit) {
                matched.add(jobSkill);
            } else {
                missing.add(jobSkill);
            }
        }

        double hitRate = matched.size() * 1.0 / jobSkills.size();
        double score = 35 * hitRate;

        return new SkillScore(score, matched, missing);
    }

    /**
     * 计算学历和经验匹配分，满分10。
     */
    private double calculateConditionScore(JobPosition job, UserJobPreference preference) {
        double score = 0;

        if (!StringUtils.hasText(job.getEducationReq())
                || !StringUtils.hasText(preference.getExpectedEducation())
                || maySatisfyEducation(preference.getExpectedEducation(), job.getEducationReq())) {
            score += 5;
        }

        if (!StringUtils.hasText(job.getExperienceReq())
                || !StringUtils.hasText(preference.getExpectedExperience())
                || maySatisfyExperience(preference.getExpectedExperience(), job.getExperienceReq())) {
            score += 5;
        }

        return score;
    }

    /**
     * 判断学历是否可能满足岗位要求。
     */
    private boolean maySatisfyEducation(String userEducation, String jobEducationReq) {
        if (jobEducationReq.contains("不限")) {
            return true;
        }

        List<String> levelOrder = List.of("专科", "本科", "硕士", "博士");

        int userIndex = findEducationIndex(userEducation, levelOrder);
        int jobIndex = findEducationIndex(jobEducationReq, levelOrder);

        if (userIndex < 0 || jobIndex < 0) {
            return true;
        }

        return userIndex >= jobIndex;
    }

    /**
     * 学历等级转下标。
     */
    private int findEducationIndex(String text, List<String> levelOrder) {
        for (int i = 0; i < levelOrder.size(); i++) {
            if (text.contains(levelOrder.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 简单判断经验是否满足。
     */
    private boolean maySatisfyExperience(String userExperience, String jobExperienceReq) {
        if (jobExperienceReq.contains("不限")) {
            return true;
        }

        /*
         * 第一版先做粗粒度判断。
         * 后续可以把经验年限抽取成数字再比较。
         */
        if (userExperience.contains("应届") && jobExperienceReq.contains("3")) {
            return false;
        }

        return true;
    }

    /**
     * 推荐等级。
     */
    private String resolveRecommendLevel(BigDecimal score) {
        double value = score.doubleValue();

        if (value >= 85) {
            return "强烈推荐";
        }
        if (value >= 70) {
            return "推荐";
        }
        if (value >= 55) {
            return "可考虑";
        }
        return "低匹配";
    }

    /**
     * 拆分关键词。
     */
    private List<String> splitKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split("[,，、/\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * 判断源文本中是否包含目标文本（不区分大小写）。
     *
     * @param source 源文本
     * @param target 目标文本
     * @return 包含返回 true，否则返回 false
     */
    private boolean containsIgnoreCase(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }

        return source.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断两个字符串是否相等（不区分大小写），任一为空返回 false。
     *
     * @param a 字符串 a
     * @param b 字符串 b
     * @return 相等返回 true，否则返回 false
     */
    private boolean equalsIgnoreCase(String a, String b) {
        return StringUtils.hasText(a)
                && StringUtils.hasText(b)
                && a.equalsIgnoreCase(b);
    }

    /**
     * 字符串去首尾空白，无有效内容时返回 null。
     *
     * @param value 原始字符串
     * @return 去空白后的字符串或 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 技能匹配中间结果。
     */
    private record SkillScore(
            double score,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {
    }
}
