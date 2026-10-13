package com.job.bootstrap.agent.intent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentIntentRouter 单元测试。
 *
 * 覆盖重点:
 * 1. 七类意图关键词路由：JOB_MATCH / RESUME_ANALYZE / JOB_SEARCH / GREETING_GENERATE
 *    / MOCK_INTERVIEW / INTERVIEW_PREPARE / JOB_FAVORITE。
 * 2. 顺序回归（关键）：含“复盘”的输入必须先命中 MOCK_INTERVIEW，
 *    不能被后面的“面试”规则抢成 INTERVIEW_PREPARE；“模拟面试”无“复盘”前缀则落入 INTERVIEW_PREPARE。
 * 3. 无法识别与空输入 -> GENERAL_CHAT。
 */
class AgentIntentRouterTest {

    private final AgentIntentRouter router = new AgentIntentRouter();

    @Test
    void shouldRouteJobMatch() {
        assertThat(router.route("帮我看看这份简历和这个岗位匹配吗")).isEqualTo(AgentIntentCode.JOB_MATCH);
    }

    @Test
    void shouldRouteResumeAnalyze() {
        // 关键词为连续子串“分析简历”，注意自然语言“分析一下简历”不会命中（见交付说明）
        assertThat(router.route("帮我分析简历")).isEqualTo(AgentIntentCode.RESUME_ANALYZE);
        assertThat(router.route("我的简历评分怎么样")).isEqualTo(AgentIntentCode.RESUME_ANALYZE);
    }

    @Test
    void shouldRouteJobSearch() {
        // 关键词为连续子串“找岗位”/“找工作”
        assertThat(router.route("帮我找岗位")).isEqualTo(AgentIntentCode.JOB_SEARCH);
        assertThat(router.route("我想找工作")).isEqualTo(AgentIntentCode.JOB_SEARCH);
    }

    @Test
    void shouldRouteGreetingGenerate() {
        assertThat(router.route("帮我生成打招呼语")).isEqualTo(AgentIntentCode.GREETING_GENERATE);
    }

    @Test
    void shouldRouteMockInterviewBeforeInterviewPrepareForReviewKeyword() {
        // “复盘模拟面试” 必须先命中 MOCK_INTERVIEW，而不是 INTERVIEW_PREPARE
        assertThat(router.route("帮我复盘模拟面试")).isEqualTo(AgentIntentCode.MOCK_INTERVIEW);
        assertThat(router.route("面试复盘一下")).isEqualTo(AgentIntentCode.MOCK_INTERVIEW);
    }

    @Test
    void shouldRouteInterviewPrepare() {
        assertThat(router.route("帮我准备面试")).isEqualTo(AgentIntentCode.INTERVIEW_PREPARE);
        // 单纯“模拟面试”没有“复盘”前缀，应落入面试准备
        assertThat(router.route("来一场模拟面试")).isEqualTo(AgentIntentCode.INTERVIEW_PREPARE);
    }

    @Test
    void shouldRouteJobFavorite() {
        assertThat(router.route("收藏这个岗位")).isEqualTo(AgentIntentCode.JOB_FAVORITE);
    }

    @Test
    void shouldRouteGeneralChatForUnknownAndEmpty() {
        assertThat(router.route("你好啊今天天气不错")).isEqualTo(AgentIntentCode.GENERAL_CHAT);
        assertThat(router.route("")).isEqualTo(AgentIntentCode.GENERAL_CHAT);
        assertThat(router.route("   ")).isEqualTo(AgentIntentCode.GENERAL_CHAT);
    }
}
