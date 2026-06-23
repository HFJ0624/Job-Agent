package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作者: hfj
 * 功能: 长期记忆动作分类器测试
 * 日期: 2026/6/23
 */
class AgentMemoryActionClassifierTest {

    private final AgentMemoryActionClassifier classifier = new AgentMemoryActionClassifier();

    @Test
    void shouldTreatMemoryQuestionAsAskOnly() {
        AgentMemoryActionDecision decision = classifier.classify("你还记得我让你叫什么名字吗？");

        assertEquals(AgentMemoryActionType.ASK_MEMORY, decision.getActionType());
        assertTrue(decision.getTargetMemoryKeys().isEmpty());
    }

    @Test
    void shouldTreatOneTimeJobSearchAsNormalChat() {
        AgentMemoryActionDecision decision = classifier.classify("帮我找北京 Java 后端岗位");

        assertEquals(AgentMemoryActionType.NORMAL_CHAT, decision.getActionType());
    }

    @Test
    void shouldTreatExplicitJobGoalAsSetMemory() {
        AgentMemoryActionDecision decision = classifier.classify("我想找北京 Java 后端岗位，最低薪资 20k");

        assertEquals(AgentMemoryActionType.SET_MEMORY, decision.getActionType());
    }

    @Test
    void shouldTreatNicknameChangeAsUpdateMemory() {
        AgentMemoryActionDecision decision = classifier.classify("以后不要叫我老黄，叫我老王");

        assertEquals(AgentMemoryActionType.UPDATE_MEMORY, decision.getActionType());
    }

    @Test
    void shouldResolveDeleteTargetKeys() {
        AgentMemoryActionDecision decision = classifier.classify("别记住我的名字和薪资要求");

        assertEquals(AgentMemoryActionType.DELETE_MEMORY, decision.getActionType());
        assertTrue(decision.getTargetMemoryKeys().contains("user_name"));
        assertTrue(decision.getTargetMemoryKeys().contains("min_salary"));
    }
}
