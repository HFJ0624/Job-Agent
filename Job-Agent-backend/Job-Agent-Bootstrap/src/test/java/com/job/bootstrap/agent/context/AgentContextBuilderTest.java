package com.job.bootstrap.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.guardrail.PiiMasker;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentContextBuilder 单元测试（任务6）。
 *
 * 覆盖重点:
 * 1. 多轮历史组装：排除当前轮、脱敏、格式化。
 * 2. 历史超预算 → 截断并标记。
 * 3. 窗口外旧历史够多 → CONTEXT_SUMMARY 生成滚动摘要并缓存（summary + summary_upto_message_id）。
 * 4. 滚动摘要失败 → 沿用旧摘要、不写库。
 * 5. 执行结果预览：脱敏 + 截断。
 * 6. 空会话 → 空上下文。
 */
class AgentContextBuilderTest {

    private final AiMessageMapper aiMessageMapper = mock(AiMessageMapper.class);
    private final AiConversationMapper aiConversationMapper = mock(AiConversationMapper.class);
    private final AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
    private final AgentGuardrailService agentGuardrailService = mock(AgentGuardrailService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentContextProperties properties = new AgentContextProperties();

    private final AgentContextBuilder builder = new AgentContextBuilder(
            aiMessageMapper, aiConversationMapper, aiModelGatewayService, agentGuardrailService, objectMapper, properties);

    @BeforeEach
    void setUp() {
        // 脱敏委托给真实 PiiMasker，验证历史/工具结果里的手机号被掩掉
        when(agentGuardrailService.maskSensitiveData(any()))
                .thenAnswer(invocation -> new PiiMasker().maskObject(invocation.getArgument(0)));
    }

    @Test
    void shouldAssembleMaskedHistoryExcludingCurrentTurn() {
        AiConversation conversation = conversation();
        // 第一条（最新）是刚保存的当前轮用户消息，应被排除
        List<AiMessage> recent = List.of(
                msg(105L, "USER", "当前问题"),
                msg(104L, "ASSISTANT", "上一轮回答"),
                msg(103L, "USER", "上一个问题 13800138000")
        );
        when(aiMessageMapper.selectList(any())).thenReturn(recent, List.of());

        AgentContextBuilder.BuiltContext built = builder.build(100L, "trace", conversation);

        assertThat(built.historyText()).contains("上一轮回答").contains("上一个问题");
        assertThat(built.historyText()).doesNotContain("当前问题");
        assertThat(built.historyText()).doesNotContain("13800138000");
        assertThat(built.rollingSummary()).isEmpty();
        assertThat(built.estimatedTokens()).isGreaterThan(0);
    }

    @Test
    void shouldTruncateHistoryOverBudget() {
        properties.setHistoryMaxChars(20);
        AiConversation conversation = conversation();
        List<AiMessage> recent = List.of(
                msg(105L, "USER", "当前"),
                msg(104L, "ASSISTANT", "回答甲回答甲回答甲"),
                msg(103L, "USER", "问题乙问题乙问题乙")
        );
        when(aiMessageMapper.selectList(any())).thenReturn(recent, List.of());

        AgentContextBuilder.BuiltContext built = builder.build(100L, "trace", conversation);

        assertThat(built.historyTruncated()).isTrue();
    }

    @Test
    void shouldGenerateRollingSummaryForOverflow() {
        AiConversation conversation = conversation();
        List<AiMessage> recent = List.of(
                msg(110L, "USER", "当前"),
                msg(109L, "ASSISTANT", "上一轮")
        );
        List<AiMessage> overflow = List.of(
                msg(108L, "USER", "旧四"), msg(107L, "ASSISTANT", "旧三"),
                msg(106L, "USER", "旧二"), msg(105L, "ASSISTANT", "旧一")
        );
        when(aiMessageMapper.selectList(any())).thenReturn(recent, overflow);
        when(aiModelGatewayService.chat(eq("CONTEXT_SUMMARY"), any(), anyString(), anyLong(), any()))
                .thenReturn("用户之前在谈Java岗位");
        when(aiConversationMapper.updateById(any(AiConversation.class))).thenReturn(1);

        AgentContextBuilder.BuiltContext built = builder.build(100L, "trace", conversation);

        assertThat(built.rollingSummary()).isEqualTo("用户之前在谈Java岗位");
        assertThat(conversation.getSummary()).isEqualTo("用户之前在谈Java岗位");
        assertThat(conversation.getSummaryUptoMessageId()).isEqualTo(108L);
        verify(aiConversationMapper).updateById(conversation);
    }

    @Test
    void rollingSummaryFailureShouldKeepExisting() {
        AiConversation conversation = conversation();
        conversation.setSummary("旧摘要");
        List<AiMessage> recent = List.of(msg(110L, "USER", "当前"), msg(109L, "ASSISTANT", "上一轮"));
        List<AiMessage> overflow = List.of(
                msg(108L, "USER", "a"), msg(107L, "ASSISTANT", "b"),
                msg(106L, "USER", "c"), msg(105L, "ASSISTANT", "d")
        );
        when(aiMessageMapper.selectList(any())).thenReturn(recent, overflow);
        when(aiModelGatewayService.chat(eq("CONTEXT_SUMMARY"), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("场景未配置"));

        AgentContextBuilder.BuiltContext built = builder.build(100L, "trace", conversation);

        assertThat(built.rollingSummary()).isEqualTo("旧摘要");
        verify(aiConversationMapper, never()).updateById(any(AiConversation.class));
    }

    @Test
    void previewExecutionShouldMaskAndTruncate() {
        properties.setExecutionPreviewChars(100);
        String preview = builder.previewExecution(Map.of("phone", "13800138000", "score", 86));
        assertThat(preview).doesNotContain("13800138000").contains("138****8000");
    }

    @Test
    void nullConversationShouldReturnEmptyContext() {
        AgentContextBuilder.BuiltContext built = builder.build(100L, "trace", null);
        assertThat(built.historyText()).isEmpty();
        assertThat(built.rollingSummary()).isEmpty();
        assertThat(built.estimatedTokens()).isZero();
    }

    private AiConversation conversation() {
        AiConversation c = new AiConversation();
        c.setId(10L);
        c.setUserId(100L);
        c.setConversationType("JOB_AGENT");
        c.setIsDeleted(0);
        return c;
    }

    private AiMessage msg(Long id, String role, String content) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(10L);
        m.setRole(role);
        m.setContent(content);
        m.setCreateTime(new Date());
        m.setIsDeleted(0);
        return m;
    }
}
