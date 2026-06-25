package com.job.bootstrap.agent.schema;

import com.job.common.agent.tool.AgentToolSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者: hfj
 * 功能: Agent 工具 Schema 注册表测试
 * 日期: 2026/6/25
 */
class AgentToolSchemaRegistryTest {

    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();

    @Test
    void shouldResolveShortToolNameToCanonicalToolName() {
        /*
         * 测试目标:
         * 1. 兼容历史短工具名 RagSearchTool。
         * 2. 系统内部统一使用 ClassName.methodName 标准工具名。
         */
        assertEquals(
                "RagSearchTool.searchKnowledge",
                registry.resolveToolName("RagSearchTool").orElseThrow()
        );
        assertEquals(
                "ResumeAnalyzeTool.analyzeResume",
                registry.resolveToolName("ResumeAnalyzeTool").orElseThrow()
        );
    }

    @Test
    void shouldFindSchemasFromMixedToolExpression() {
        /*
         * 测试目标:
         * 1. Planner 可能生成 A / B 形式的候选工具表达式。
         * 2. 表达式里即使混用短名和标准名，也要解析成已注册 Schema。
         */
        List<String> toolNames = registry.findByToolExpression(
                        "RagSearchTool / JobSearchTool.searchJobs"
                )
                .stream()
                .map(AgentToolSchema::getToolName)
                .toList();

        assertEquals(2, toolNames.size());
        assertTrue(toolNames.contains("RagSearchTool.searchKnowledge"));
        assertTrue(toolNames.contains("JobSearchTool.searchJobs"));
    }
}
