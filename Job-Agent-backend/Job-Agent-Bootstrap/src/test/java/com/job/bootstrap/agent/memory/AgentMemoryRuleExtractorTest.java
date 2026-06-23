package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者: hfj
 * 功能: 长期记忆规则抽取器测试
 * 日期: 2026/6/23
 */
class AgentMemoryRuleExtractorTest {

    private final AgentMemoryRuleExtractor extractor = new AgentMemoryRuleExtractor();

    @Test
    void shouldRememberAssistantNickname() {
        List<AgentMemoryCandidate> candidates = extractor.extract("以后你叫黄锋森AI助手");

        AgentMemoryCandidate nickname = candidates.stream()
                .filter(candidate -> "assistant_nickname".equals(candidate.getMemoryKey()))
                .findFirst()
                .orElseThrow();

        assertEquals(AgentMemoryType.COMMUNICATION_STYLE, nickname.getMemoryType());
        assertEquals("黄锋森AI助手", nickname.getMemoryValue());
    }

    @Test
    void shouldRememberJobPreference() {
        List<AgentMemoryCandidate> candidates = extractor.extract("我更喜欢北京的 Java 后端岗位，最低薪资 20k");

        assertTrue(candidates.stream().anyMatch(candidate ->
                "preferred_city".equals(candidate.getMemoryKey()) && "北京".equals(candidate.getMemoryValue())));
        assertTrue(candidates.stream().anyMatch(candidate ->
                "target_role".equals(candidate.getMemoryKey()) && "Java 后端".equals(candidate.getMemoryValue())));
        assertTrue(candidates.stream().anyMatch(candidate ->
                "min_salary".equals(candidate.getMemoryKey()) && "20k".equals(candidate.getMemoryValue())));
    }

    @Test
    void shouldRespectDoNotRememberInstruction() {
        List<AgentMemoryCandidate> candidates = extractor.extract("不要记住我的手机号 13812345678");

        assertTrue(candidates.isEmpty());
    }
}
