package com.job.bootstrap.service.match;

import com.job.common.entity.position.JobPosition;
import com.job.common.vo.preference.UserJobPreferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HardConditionEvaluator 单元测试（任务 J5）。
 *
 * 覆盖重点:
 * 1. 城市/岗位方向/薪资/学历四类硬性违反；
 * 2. 本轮显式参数优先于长期偏好；
 * 3. 无偏好数据时全部通过（不拿猜测当条件）；
 * 4. 远程岗位不受城市约束；岗位未标城市不判违反。
 */
class HardConditionEvaluatorTest {

    private final HardConditionEvaluator evaluator = new HardConditionEvaluator();

    private UserJobPreferenceVO preference;

    @BeforeEach
    void setUp() {
        preference = new UserJobPreferenceVO();
        preference.setExpectedCity("上海");
        preference.setExpectedJobTitle("Java");
        preference.setMinSalary(25);
        preference.setExpectedEducation("本科");
    }

    private JobPosition job(String city, String title, Integer minSalary, Integer maxSalary, String education) {
        JobPosition job = new JobPosition();
        job.setCity(city);
        job.setJobTitle(title);
        job.setJobDescription("负责后端系统开发");
        job.setMinSalary(minSalary);
        job.setMaxSalary(maxSalary);
        job.setEducationReq(education);
        return job;
    }

    private HardConditionEvaluator.HardConditionResult evaluate(Map<String, Object> explicit, JobPosition job) {
        return evaluator.evaluate(new HardConditionEvaluator.HardConditionInput(preference, explicit, job));
    }

    @Test
    void matchingJobShouldPass() {
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job("上海", "Java 后端开发", 20, 35, "本科"));

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.appliedConditions()).contains("city", "jobTitle", "minSalary", "education");
    }

    @Test
    void cityMismatchShouldViolate() {
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job("北京", "Java 后端开发", 20, 35, "本科"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("城市不符") && v.contains("上海") && v.contains("北京"));
    }

    @Test
    void salaryBelowExpectationShouldViolate() {
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job("上海", "Java 后端开发", 10, 15, "本科"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("薪资不符") && v.contains("25") && v.contains("15"));
    }

    @Test
    void educationMismatchShouldViolate() {
        // 期望本科，岗位要求硕士
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job("上海", "Java 后端开发", 20, 35, "硕士"));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("学历不符"));
    }

    @Test
    void explicitParamsShouldOverridePreference() {
        // 长期偏好上海，但本轮明说“北京也可以”——北京岗位应通过
        Map<String, Object> explicit = new LinkedHashMap<>();
        explicit.put("city", "北京");
        HardConditionEvaluator.HardConditionResult result =
                evaluate(explicit, job("北京", "Java 后端开发", 20, 35, "本科"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void remoteJobShouldBypassCity() {
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job("远程", "Java 后端开发", 20, 35, "本科"));

        assertThat(result.passed()).isTrue();
        assertThat(result.appliedConditions()).doesNotContain("city");
    }

    @Test
    void noPreferenceShouldPassEverything() {
        HardConditionEvaluator.HardConditionResult result = evaluator.evaluate(
                new HardConditionEvaluator.HardConditionInput(null, Map.of(), job("北京", "产品经理", 5, 8, "大专")));

        assertThat(result.passed()).isTrue();
        assertThat(result.appliedConditions()).isEmpty();
    }

    @Test
    void jobWithoutCityShouldNotViolateCity() {
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), job(null, "Java 后端开发", 20, 35, "本科"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void titleKeywordShouldMatchDescription() {
        // 期望关键词被岗位描述包含也算匹配（标题本身不含关键词）
        JobPosition backendEngineer = job("上海", "后端工程师", 20, 35, "本科");
        backendEngineer.setJobDescription("负责Java后端系统开发与接口优化");
        HardConditionEvaluator.HardConditionResult result =
                evaluate(Map.of(), backendEngineer);

        assertThat(result.passed()).isTrue();
    }
}
