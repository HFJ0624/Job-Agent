package com.job.bootstrap.service.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobMatchRecordMapper;
import com.job.bootstrap.service.JobPositionService;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.bootstrap.service.impl.JobMatchServiceImpl;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
import com.job.common.vo.preference.UserJobPreferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 标注样本排序评估测试（任务 J5 验收：Top-K 相关性 / NDCG@K / 硬性违反率 / 证据覆盖）。
 *
 * 数据说明（诚实边界）:
 * 1. 标注集为**构造样本**（同一份 Java 后端简历 × 10 个岗位），相关度 0-3 由测试预设，
 *     用于验证“规则 + 硬性条件”相对“纯技能分排序”的排序收益，不是真实人工标注；
 * 2. 真实人工标注（简历-岗位对 + 标注理由）需要业务方介入，属于 VM/数据侧待办；
 * 3. 对比口径：baseline = 按技能分排序（旧行为代理，对城市/薪资违规无感知），
 *     new = 按推荐分排序（硬性违反封顶 50）。
 */
class MatchRankingLabeledTest {

    private static final Long USER_ID = 100L;
    private static final Long RESUME_ID = 1L;

    private static final String RESUME_TEXT = "张三，本科，3年Java后端开发经验。熟悉Spring Boot、MySQL、Redis、消息队列。"
            + "在电商系统中负责订单模块，接口优化后响应时间降低50%，QPS 3000。";

    /** 一个标注样本：岗位 + 预设相关度（0-3）+ 是否期望被硬性条件拦截。 */
    private record LabeledSample(String name, JobPosition job, int relevance, boolean expectViolation) {
    }

    /** 样本 + 匹配结果配对（避免 indexOf 脆弱匹配）。 */
    private record SampleResult(LabeledSample sample, JobMatchVO vo) {
    }

    private final JobResumeService jobResumeService = mock(JobResumeService.class);
    private final JobPositionService jobPositionService = mock(JobPositionService.class);
    private final UserJobPreferenceService userJobPreferenceService = mock(UserJobPreferenceService.class);
    private final JobMatchRecordMapper recordMapper = mock(JobMatchRecordMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JobMatchServiceImpl service = new JobMatchServiceImpl(
            jobResumeService, jobPositionService, objectMapper,
            userJobPreferenceService, new HardConditionEvaluator(), new MatchEvidenceExtractor());

    private final List<LabeledSample> samples = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", recordMapper);
        when(recordMapper.insert(any(com.job.common.entity.match.JobMatchRecord.class))).thenReturn(1);

        JobResume resume = new JobResume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);
        resume.setResumeName("张三简历");
        resume.setRawText(RESUME_TEXT);
        when(jobResumeService.getUserResumeRequired(USER_ID, RESUME_ID)).thenReturn(resume);

        // 用户画像：人在上海、找 Java、期望不低于 25K、本科学历
        UserJobPreferenceVO preference = new UserJobPreferenceVO();
        preference.setExpectedCity("上海");
        preference.setExpectedJobTitle("Java");
        preference.setMinSalary(25);
        preference.setExpectedEducation("本科");
        when(userJobPreferenceService.getPreference(USER_ID)).thenReturn(preference);

        /*
         * 构造标注集（同一简历 × 10 岗位）；第 4 个字段 = 是否应被硬性条件拦截。
         * 顺序模拟搜索引擎返回序（不按相关度预排）——否则平分会让 baseline 白捡理想序。
         */
        long jobId = 100;
        samples.add(new LabeledSample("北京Java", job(jobId++, "北京", "Java 后端开发", 25, 40, "本科", "Java,Spring Boot,MySQL,Redis"), 1, true)); // 城市违反
        samples.add(new LabeledSample("上海Java-20-35K", job(jobId++, "上海", "Java 后端开发", 20, 35, "本科", "Java,Spring Boot,MySQL,Redis"), 3, false));
        samples.add(new LabeledSample("上海前端Vue", job(jobId++, "上海", "前端工程师", 20, 35, "本科", "Vue,TypeScript,React"), 1, true)); // 方向违反
        samples.add(new LabeledSample("上海Java-25-40K", job(jobId++, "上海", "Java 后端开发", 25, 40, "本科", "Java,Spring Boot,MySQL,Redis"), 3, false));
        samples.add(new LabeledSample("杭州Java", job(jobId++, "杭州", "Java 后端开发", 25, 40, "本科", "Java,Spring Boot,MySQL,Redis"), 2, true)); // 城市违反
        samples.add(new LabeledSample("上海产品经理", job(jobId++, "上海", "产品经理", 20, 35, "本科", "需求分析,原型设计"), 0, true)); // 方向违反
        samples.add(new LabeledSample("上海Java+Go", job(jobId++, "上海", "Java 后端开发(Go 方向)", 22, 38, "本科", "Java,Spring Boot,Go"), 2, false));
        samples.add(new LabeledSample("上海Java-10-15K", job(jobId++, "上海", "Java 后端开发", 10, 15, "本科", "Java,Spring Boot,MySQL,Redis"), 2, true)); // 薪资违反
        samples.add(new LabeledSample("上海Java实习助理", job(jobId++, "上海", "Java 实习助理", 8, 12, "大专", "Java,Spring Boot"), 2, true)); // 薪资 12K < 25K
        samples.add(new LabeledSample("深圳Java", job(jobId++, "深圳", "Java 后端开发", 25, 40, "本科", "Java,Spring Boot,MySQL,Redis"), 1, true)); // 城市违反
    }

    private JobPosition job(Long id, String city, String title, int minSalary, int maxSalary,
                            String education, String skills) {
        JobPosition job = new JobPosition();
        job.setId(id);
        job.setCity(city);
        job.setJobTitle(title);
        job.setJobDescription("负责系统开发、接口优化与性能提升");
        job.setMinSalary(minSalary);
        job.setMaxSalary(maxSalary);
        job.setEducationReq(education);
        job.setExperienceReq("3年");
        job.setSkillKeywords(skills);
        return job;
    }

    /** 跑完整链路，返回样本-结果配对。 */
    private List<SampleResult> runPipeline() {
        List<SampleResult> results = new ArrayList<>();
        for (LabeledSample sample : samples) {
            when(jobPositionService.getById(sample.job().getId())).thenReturn(sample.job());
            results.add(new SampleResult(sample, service.matchJob(USER_ID, RESUME_ID, sample.job().getId())));
        }
        return results;
    }

    @Test
    void newRankingShouldBeatBaselineOnLabeledSet() {
        List<SampleResult> results = runPipeline();

        // 新排序：按推荐分（硬性违反已封顶 50，必然沉到通过者之后）
        List<SampleResult> newRanked = results.stream()
                .sorted(Comparator.comparing((SampleResult r) -> r.vo().getMatchScore().doubleValue()).reversed())
                .toList();
        List<Integer> newRelevances = newRanked.stream().map(r -> r.sample().relevance()).toList();

        // baseline 代理排序：按技能分（旧行为，对硬性违规无感知）
        List<SampleResult> baselineRanked = results.stream()
                .sorted(Comparator.comparing((SampleResult r) -> r.vo().getSkillScore().doubleValue()).reversed())
                .toList();
        List<Integer> baselineRelevances = baselineRanked.stream().map(r -> r.sample().relevance()).toList();

        double newNdcg = MatchNdcgEvaluator.ndcgAtK(newRelevances, 5);
        double baselineNdcg = MatchNdcgEvaluator.ndcgAtK(baselineRelevances, 5);

        /*
         * 核心验收：硬性条件让排序变好。
         * 水位说明：0.9 是这套构造样本的合理上限——北京/杭州两个样本维度分完全相同
         * （同技能同薪资仅城市不同），打分上不可区分；不为凑 1.0 去改数据。
         */
        assertThat(newNdcg).isGreaterThan(baselineNdcg);
        assertThat(newNdcg).isGreaterThanOrEqualTo(0.9d);
        assertThat(MatchNdcgEvaluator.precisionAtK(newRelevances, 5)).isGreaterThanOrEqualTo(0.6d);

        // 名次断言：新排序里所有“通过者”必须排在所有“违反者”之前
        int lastPassingRank = -1;
        int firstViolatingRank = Integer.MAX_VALUE;
        for (int i = 0; i < newRanked.size(); i++) {
            if (newRanked.get(i).vo().getHardConditionPassed()) {
                lastPassingRank = i;
            } else if (i < firstViolatingRank) {
                firstViolatingRank = i;
            }
        }
        assertThat(lastPassingRank).isLessThan(firstViolatingRank);

        // baseline（纯技能分）做不到：至少一个违反者排在通过者前面——证明旧行为确实有错
        boolean baselineInterleaved = false;
        boolean seenPassing = false;
        for (SampleResult r : baselineRanked) {
            if (r.vo().getHardConditionPassed()) {
                seenPassing = true;
            } else if (seenPassing) {
                baselineInterleaved = true;
                break;
            }
        }
        assertThat(baselineInterleaved).isTrue();
    }

    @Test
    void everyHardViolatedSampleShouldBeBlocked() {
        List<SampleResult> results = runPipeline();

        long violatedCount = results.stream().filter(r -> !r.vo().getHardConditionPassed()).count();
        long blockedCount = results.stream()
                .filter(r -> !r.vo().getHardConditionPassed())
                .filter(r -> Boolean.FALSE.equals(r.vo().getRecommendApply()))
                .count();

        // 7 个构造违规样本全部被拦截，且与标注预期一致
        assertThat(violatedCount).isEqualTo(7);
        assertThat(blockedCount).isEqualTo(violatedCount);
        // 标注预期与实测一致：expectViolation=true 对应 passed=false（相等即矛盾）
        assertThat(results.stream()
                .filter(r -> r.sample().expectViolation() == r.vo().getHardConditionPassed())
                .map(r -> r.sample().name())
                .toList()).isEmpty();
    }

    @Test
    void javaSamplesShouldCarrySkillEvidence() {
        List<SampleResult> results = runPipeline();

        // 8 个 Java 相关样本都有技能证据；前端/产品样本没有（技能不匹配，不进证据列）
        long withSkillEvidence = results.stream()
                .filter(r -> r.vo().getEvidences().stream().anyMatch(e -> "SKILL".equals(e.getType())))
                .count();
        assertThat(withSkillEvidence).isEqualTo(8);

        // 证据忠于原文：每条 SKILL 证据的片段必须出现在简历原文里
        for (SampleResult r : results) {
            for (com.job.common.vo.match.MatchEvidenceVO evidence : r.vo().getEvidences()) {
                if ("SKILL".equals(evidence.getType()) && evidence.getEvidence() != null) {
                    assertThat(RESUME_TEXT.replace("…", ""))
                            .contains(evidence.getEvidence().replace("…", ""));
                }
            }
        }
    }
}
