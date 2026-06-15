package com.job.bootstrap.service.resume;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者:hfj
 * 功能:AI 简历评分 V2 规则引擎测试
 * 日期:2026/6/15
 *
 * 测试说明:
 * 1. 这里不启动 Spring，也不调用真实大模型，保证没有 API Key 时测试也能运行。
 * 2. 测试重点是规则引擎的结构稳定性: 总分、八维分、等级、优势、不足、建议都必须可用。
 * 3. LLM 的质量属于运行时能力，失败时业务 Service 会使用规则结果兜底。
 */
class ResumeScoreRuleEngineTest {

    private final ResumeScoreRuleEngine ruleEngine = new ResumeScoreRuleEngine();

    @Test
    void calculateShouldReturnStableV2Score() {
        String resumeText = """
                黄锋杰
                手机: 13812345678
                邮箱: hfj@example.com
                现居城市: 上海
                GitHub: https://github.com/example/job-agent
                求职意向: Java 后端开发 / AI Agent 应用开发
                
                教育背景
                沈阳航空航天大学 计算机科学与技术 本科 2022-2026
                主修课程: 数据结构、计算机网络、操作系统、数据库、软件工程
                获得校级奖学金，CET-6
                
                技能栈
                Languages: Java, Python, TypeScript, SQL
                Backend: Spring Boot, Spring Cloud, MyBatis-Plus, LangChain4j
                Database: MySQL, PostgreSQL, Redis, pgvector
                AI / Agent: RAG, Embedding, Tool Calling, MCP, Prompt
                DevOps: Git, Maven, Docker, Linux, Nginx
                
                项目经历
                项目名称: Job-Agent 智能求职助手
                项目背景: 面向求职者的 AI Agent 求职辅助平台。
                技术栈: Spring Boot, MyBatis-Plus, Vue, PostgreSQL, pgvector, LangChain4j
                我的职责: 负责简历解析、RAG 知识库、ResumeAnalyzeTool 和 Agent Trace 调用链路设计与实现。
                核心功能: 支持简历、岗位、公司、沟通记录入库，使用 pgvector 进行向量检索，并让 AI 助手基于检索结果回答。
                技术难点: 解决 Embedding 维度和 pgvector 索引限制问题，并记录工具调用输入输出。
                项目成果: 将检索结果默认召回 4 条，工具调用成功率达到 95%，接口响应时间从 900ms 优化到 300ms。
                
                实习经历
                某科技公司 Java 后端实习生 2025.06-2025.09
                负责订单模块接口开发，使用 Spring Boot + MyBatis 实现订单查询、状态流转和权限校验。
                将接口平均响应时间从 800ms 降低到 320ms，支持 10 万级订单数据查询。
                """;

        ResumeScoreRuleEngine.RuleScoreResult result = ruleEngine.calculate(resumeText, "AI Agent 开发");

        assertNotNull(result);
        assertEquals("V2", result.getScoreVersion());
        assertNotNull(result.getScoreBreakdown());
        assertEquals(8, result.getDimensions().size());
        assertEquals(result.getOverallScore(), result.getDimensions().stream()
                .mapToInt(ResumeScoreRuleEngine.ScoreDimension::getScore)
                .sum());
        assertTrue(result.getOverallScore() >= 0 && result.getOverallScore() <= 100);
        assertFalse(result.getStrengths().isEmpty());
        assertFalse(result.getWeaknesses().isEmpty());
        assertFalse(result.getImprovementSuggestions().isEmpty());
    }

    @Test
    void dimensionScoreShouldNotExceedMaxScore() {
        ResumeScoreRuleEngine.RuleScoreResult result = ruleEngine.calculate("只有一段很短的简历文本", null);

        for (ResumeScoreRuleEngine.ScoreDimension dimension : result.getDimensions()) {
            assertTrue(dimension.getScore() >= 0);
            assertTrue(dimension.getScore() <= dimension.getMaxScore());
        }
    }
}
