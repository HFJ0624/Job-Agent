package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobMatchRecordMapper;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.bootstrap.service.match.HardConditionEvaluator;
import com.job.bootstrap.service.match.MatchEvidenceExtractor;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
import com.job.common.vo.preference.UserJobPreferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JobMatchServiceImpl 任务 J5 集成测试。
 *
 * 覆盖重点:
 * 1. 硬性违反：recommendApply=false、等级“硬性条件不符”、推荐分封顶、违反项进风险点；
 * 2. 硬性通过：正常评分与等级；
 * 3. 偏好分真实化：无偏好给中性分（不再虚高占位）；
 * 4. 证据挂载：VO 带 evidences；
 * 5. 本轮显式参数优先于长期偏好。
 */
class JobMatchServiceImplTest {

    private static final Long USER_ID = 100L;
    private static final Long RESUME_ID = 1L;
    private static final Long JOB_ID = 2L;

    private static final String RESUME_TEXT = "张三，本科，3年Java后端开发经验。熟悉Spring Boot、MySQL、Redis、消息队列。"
            + "在电商系统中负责订单模块，接口优化后响应时间降低50%，QPS 3000。";

    private final JobResumeService jobResumeService = mock(JobResumeService.class);
    private final JobPositionService jobPositionService = mock(JobPositionService.class);
    private final UserJobPreferenceService userJobPreferenceService = mock(UserJobPreferenceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobMatchRecordMapper recordMapper = mock(JobMatchRecordMapper.class);

    private final JobMatchServiceImpl service = new JobMatchServiceImpl(
            jobResumeService, jobPositionService, objectMapper,
            userJobPreferenceService, new HardConditionEvaluator(), new MatchEvidenceExtractor());

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 是字段注入，单测里用反射装上 Mock
        ReflectionTestUtils.setField(service, "baseMapper", recordMapper);
        when(recordMapper.insert(any(com.job.common.entity.match.JobMatchRecord.class))).thenReturn(1);

        JobResume resume = new JobResume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setResumeName("张三简历");
        resume.setRawText(RESUME_TEXT);
        when(jobResumeService.getUserResumeRequired(USER_ID, RESUME_ID)).thenReturn(resume);
    }

    private UserJobPreferenceVO shanghaiJavaPreference() {
        UserJobPreferenceVO preference = new UserJobPreferenceVO();
        preference.setExpectedCity("上海");
        preference.setExpectedJobTitle("Java");
        preference.setMinSalary(25);
        preference.setExpectedEducation("本科");
        return preference;
    }

    private JobPosition job(String city, String title, int minSalary, int maxSalary, String education, String skills) {
        JobPosition job = new JobPosition();
        job.setId(JOB_ID);
        job.setCity(city);
        job.setJobTitle(title);
        job.setJobDescription("负责后端系统开发与接口优化");
        job.setMinSalary(minSalary);
        job.setMaxSalary(maxSalary);
        job.setEducationReq(education);
        job.setExperienceReq("3年");
        job.setSkillKeywords(skills);
        return job;
    }

    @Test
    void hardViolationShouldBlockRecommendationAndCapScore() {
        when(userJobPreferenceService.getPreference(USER_ID)).thenReturn(shanghaiJavaPreference());
        when(jobPositionService.getById(JOB_ID))
                .thenReturn(job("北京", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"));

        JobMatchVO vo = service.matchJob(USER_ID, RESUME_ID, JOB_ID);

        assertThat(vo.getHardConditionPassed()).isFalse();
        assertThat(vo.getHardConditionViolations()).anyMatch(v -> v.contains("城市不符"));
        assertThat(vo.getRecommendApply()).isFalse();
        assertThat(vo.getMatchLevel()).isEqualTo("硬性条件不符");
        // 推荐分封顶 50（低于“一般匹配”55），排序上让位
        assertThat(vo.getMatchScore().doubleValue()).isLessThanOrEqualTo(50d);
        // 违反项进风险点（持久化可审计）
        assertThat(vo.getRiskPoints()).anyMatch(r -> r.contains("城市不符"));
    }

    @Test
    void matchingJobShouldPassAndCarryEvidences() {
        when(userJobPreferenceService.getPreference(USER_ID)).thenReturn(shanghaiJavaPreference());
        when(jobPositionService.getById(JOB_ID))
                .thenReturn(job("上海", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"));

        JobMatchVO vo = service.matchJob(USER_ID, RESUME_ID, JOB_ID);

        assertThat(vo.getHardConditionPassed()).isTrue();
        assertThat(vo.getHardConditionViolations()).isEmpty();
        assertThat(vo.getMatchScore().doubleValue()).isGreaterThan(55d);
        // 技能证据来自简历原文
        assertThat(vo.getEvidences()).isNotEmpty();
        assertThat(vo.getEvidences()).anyMatch(e ->
                "SKILL".equals(e.getType()) && "Spring Boot".equals(e.getRequirement()) && e.getEvidence().contains("Spring Boot"));
        // 条件证据：学历要求有原文佐证
        assertThat(vo.getEvidences()).anyMatch(e ->
                "CONDITION".equals(e.getType()) && e.getRequirement().contains("学历") && Boolean.TRUE.equals(e.getMatched()));
    }

    @Test
    void noPreferenceShouldUseNeutralScoreAndPass() {
        when(userJobPreferenceService.getPreference(USER_ID)).thenReturn(null);
        when(jobPositionService.getById(JOB_ID))
                .thenReturn(job("北京", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"));

        JobMatchVO vo = service.matchJob(USER_ID, RESUME_ID, JOB_ID);

        // 无偏好 = 无硬性条件，不误伤
        assertThat(vo.getHardConditionPassed()).isTrue();
        // 偏好分中性 60（不再是虚高占位 80）
        assertThat(vo.getPreferenceScore().doubleValue()).isEqualTo(60d);
    }

    @Test
    void explicitCityShouldOverridePreference() {
        when(userJobPreferenceService.getPreference(USER_ID)).thenReturn(shanghaiJavaPreference());
        when(jobPositionService.getById(JOB_ID))
                .thenReturn(job("北京", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"));

        JobMatchVO vo = service.matchJob(USER_ID, RESUME_ID, JOB_ID, Map.of("city", "北京"));

        assertThat(vo.getHardConditionPassed()).isTrue();
    }

    @Test
    void preferenceFailureShouldNotBreakMatching() {
        when(userJobPreferenceService.getPreference(USER_ID)).thenThrow(new RuntimeException("偏好表异常"));
        when(jobPositionService.getById(JOB_ID))
                .thenReturn(job("上海", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"));

        JobMatchVO vo = service.matchJob(USER_ID, RESUME_ID, JOB_ID);

        // 偏好加载失败按无偏好处理，匹配不中断
        assertThat(vo.getHardConditionPassed()).isTrue();
        assertThat(vo.getMatchScore()).isNotNull();
    }
}
