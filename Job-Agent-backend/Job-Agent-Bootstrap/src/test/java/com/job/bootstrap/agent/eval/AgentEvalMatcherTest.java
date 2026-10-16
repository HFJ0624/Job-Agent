package com.job.bootstrap.agent.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentEvalMatcher 单元测试（任务10 质量门禁）。
 *
 * 覆盖重点:
 * 1. strictMatch 只有“完全一致”才命中：下划线/驼峰/全半角/大小写/空白变体全部不匹配。
 * 2. toolStrictMatch 允许全名或类名（'.' 前）精确，但拒绝任意子串。
 */
class AgentEvalMatcherTest {

    private final AgentEvalMatcher matcher = new AgentEvalMatcher();

    @Test
    void exactMatchShouldPass() {
        assertThat(matcher.strictMatch("JobSearchTool", "JobSearchTool")).isTrue();
        assertThat(matcher.strictMatch("Java", "Java")).isTrue();
    }

    @Test
    void underscoreVariantShouldFail() {
        assertThat(matcher.strictMatch("job_search_tool", "JobSearchTool")).isFalse();
    }

    @Test
    void pascalCaseAndCaseVariantShouldFail() {
        assertThat(matcher.strictMatch("jobSearchTool", "JobSearchTool")).isFalse(); // 首字母大小写
        assertThat(matcher.strictMatch("JAVA", "Java")).isFalse();                    // 全大写
    }

    @Test
    void fullWidthVariantShouldFail() {
        assertThat(matcher.strictMatch("Ｊｏｂ", "Job")).isFalse();
    }

    @Test
    void whitespaceVariantShouldFail() {
        assertThat(matcher.strictMatch("Ja va", "Java")).isFalse();   // 中间空白
        assertThat(matcher.strictMatch(" Java", "Java")).isFalse();   // 前导空白
        assertThat(matcher.strictMatch("Java ", "Java")).isFalse();   // 尾随空白
    }

    @Test
    void blankOrNullShouldFail() {
        assertThat(matcher.strictMatch(null, "Java")).isFalse();
        assertThat(matcher.strictMatch("Java", null)).isFalse();
        assertThat(matcher.strictMatch("  ", "Java")).isFalse();
    }

    @Test
    void toolStrictMatchAllowsFullNameOrClassName() {
        assertThat(matcher.toolStrictMatch("JobSearchTool.searchJobs", "JobSearchTool")).isTrue();
        assertThat(matcher.toolStrictMatch("JobSearchTool.searchJobs", "JobSearchTool.searchJobs")).isTrue();
    }

    @Test
    void toolStrictMatchRejectsSubstring() {
        assertThat(matcher.toolStrictMatch("JobSearchTool.searchJobs", "Search")).isFalse();     // 旧 contains 会误过
        assertThat(matcher.toolStrictMatch("job_match_tool.match", "JobMatchTool")).isFalse();   // 下划线+小写
    }
}
