package com.job.bootstrap.service.eval;

import com.job.bootstrap.service.impl.AgentEvalCoreTemplateFactory;
import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.vo.agent.AgentEvalCoreTemplateCreateResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent Eval 核心链路模板工厂测试。
 *
 * 说明:
 * 1. 模板工厂是纯规则逻辑，不依赖数据库和模型。
 * 2. 它负责决定“应该生成哪些基础用例”，Service 层只负责把这些用例保存到数据库。
 * 3. 这里先锁定第一版策略: 五类核心链路模板齐全，已有同类型模板时默认跳过。
 */
class AgentEvalCoreTemplateFactoryTest {

    @Test
    void shouldCreateFiveCoreTemplatesWhenDatasetHasNoTemplate() {
        AgentEvalCoreTemplateFactory factory = new AgentEvalCoreTemplateFactory();

        AgentEvalCoreTemplateCreateResultVO result = factory.buildTemplates(1L, 7L, false, List.of());

        assertThat(result.getCreatedCases()).hasSize(5);
        assertThat(result.getSkippedTypes()).isEmpty();
        assertThat(result.getCreatedCases())
                .extracting(AgentEvalCase::getEvalType)
                .containsExactlyInAnyOrder("TOOL_CALL", "RAG_RETRIEVAL", "MEMORY_RECALL", "GUARDRAIL", "JSON_OUTPUT");
        assertThat(result.getCreatedCases())
                .allSatisfy(item -> {
                    assertThat(item.getDatasetId()).isEqualTo(1L);
                    assertThat(item.getUserId()).isEqualTo(7L);
                    assertThat(item.getEnableStatus()).isEqualTo(1);
                    assertThat(item.getTags()).contains("CORE_TEMPLATE");
                });
    }

    @Test
    void shouldSkipExistingCoreTemplateTypesByDefault() {
        AgentEvalCoreTemplateFactory factory = new AgentEvalCoreTemplateFactory();
        AgentEvalCase existing = new AgentEvalCase();
        existing.setEvalType("TOOL_CALL");
        existing.setTags("CORE_TEMPLATE,TOOL_CALL");

        AgentEvalCoreTemplateCreateResultVO result = factory.buildTemplates(1L, 7L, false, List.of(existing));

        assertThat(result.getCreatedCases()).hasSize(4);
        assertThat(result.getSkippedTypes()).containsExactly("TOOL_CALL");
        assertThat(result.getCreatedCases())
                .extracting(AgentEvalCase::getEvalType)
                .doesNotContain("TOOL_CALL");
    }

    @Test
    void shouldRecreateExistingTypesWhenOverwriteEnabled() {
        AgentEvalCoreTemplateFactory factory = new AgentEvalCoreTemplateFactory();
        AgentEvalCase existing = new AgentEvalCase();
        existing.setEvalType("TOOL_CALL");
        existing.setTags("CORE_TEMPLATE,TOOL_CALL");

        AgentEvalCoreTemplateCreateResultVO result = factory.buildTemplates(1L, 7L, true, List.of(existing));

        assertThat(result.getCreatedCases()).hasSize(5);
        assertThat(result.getSkippedTypes()).isEmpty();
    }
}
