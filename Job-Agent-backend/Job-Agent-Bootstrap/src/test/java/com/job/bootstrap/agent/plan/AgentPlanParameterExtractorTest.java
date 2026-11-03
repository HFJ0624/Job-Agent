package com.job.bootstrap.agent.plan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 作者: hfj
 * 功能: Agent 计划参数抽取器测试
 * 日期: 2026/6/25
 */
class AgentPlanParameterExtractorTest {

    private final AgentPlanParameterExtractor extractor = new AgentPlanParameterExtractor();

    @Test
    void shouldExtractResumeNameAndJobTitleFromQuotedMessage() {
        Map<String, Object> params = extractor.extract("帮我分析「黄锋杰(后端)简历」和「Java 后端开发」是否匹配");

        assertEquals("黄锋杰(后端)简历", params.get("resumeName"));
        assertEquals("Java 后端开发", params.get("jobTitle"));
    }

    @Test
    void shouldStillExtractLegacyIds() {
        Map<String, Object> params = extractor.extract("帮我分析 resumeId=1 和 jobId=2 的岗位匹配度");

        assertEquals(1L, params.get("resumeId"));
        assertEquals(2L, params.get("jobId"));
    }

    @Test
    void shouldExtractEducationRequirementFromNaturalLanguage() {
        // 明确作为岗位要求
        assertEquals("本科", extractor.extract("帮我找上海Java岗位，要求本科以上").get("educationReq"));
        assertEquals("硕士", extractor.extract("找硕士及以上的后端岗位").get("educationReq"));
        // 用户自身学历不能被当成岗位要求
        assertEquals(false, extractor.extract("我本科毕业，想找Java岗位").containsKey("educationReq"));
    }
}
