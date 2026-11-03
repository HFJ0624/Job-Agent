package com.job.bootstrap.agent.react;

import com.job.bootstrap.ai.turn.AiChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 功能: 单次 run 的运行上下文（第一阶段 ReAct 内核）。
 *
 * 设计说明:
 * 1. 每个 run 独占一份消息列表与预算计数——**禁止全局共享可变历史**（文档红线）；
 * 2. 消息列表是持久化与恢复的唯一事实来源：assistant tool_calls 与 tool 结果必须成对，
 *    暂停/结束时也要补齐配对的 tool 消息；
 * 3. 预算判定集中在这里（轮次/工具调用/截止时间），Runner 只读不猜。
 */
public class AgentRunContext {

    private final List<AiChatMessage> messages = new ArrayList<>();
    private final long startedAtMillis;
    private int turn;
    private int toolCalls;

    public AgentRunContext(String systemPrompt, String userMessage) {
        this.startedAtMillis = System.currentTimeMillis();
        this.messages.add(AiChatMessage.system(systemPrompt));
        this.messages.add(AiChatMessage.user(userMessage));
    }

    /** 从已持久化的消息列表恢复（续跑）。 */
    public AgentRunContext(List<AiChatMessage> history, int turn, int toolCalls, long startedAtMillis) {
        if (history != null) {
            this.messages.addAll(history);
        }
        this.turn = turn;
        this.toolCalls = toolCalls;
        this.startedAtMillis = startedAtMillis <= 0 ? System.currentTimeMillis() : startedAtMillis;
    }

    public void append(AiChatMessage message) {
        messages.add(message);
    }

    /** 消息快照（外部只读）。 */
    public List<AiChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    /** 进入下一轮。 */
    public int nextTurn() {
        return ++turn;
    }

    public int turn() {
        return turn;
    }

    public int toolCalls() {
        return toolCalls;
    }

    public int countToolCall() {
        return ++toolCalls;
    }

    public long elapsedMillis() {
        return System.currentTimeMillis() - startedAtMillis;
    }

    public boolean deadlineExceeded(AgentDefinition definition) {
        return elapsedMillis() >= TimeUnit.SECONDS.toMillis(definition.deadlineSeconds());
    }

    public boolean turnsExhausted(AgentDefinition definition) {
        return turn >= definition.maxTurns();
    }

    public boolean toolCallsExhausted(AgentDefinition definition) {
        return toolCalls >= definition.maxToolCalls();
    }
}
