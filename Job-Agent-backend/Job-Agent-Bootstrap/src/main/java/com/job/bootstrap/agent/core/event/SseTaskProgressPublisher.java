package com.job.bootstrap.agent.core.event;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 功能: SSE 进度发布器（第三阶段）。
 *
 * 设计说明:
 * 1. 与 Web 层解耦：只依赖 `send(name, data)` 回调，控制器用 SseEmitter 适配；
 * 2. 同一任务内 seq 原子递增——并行节点发布的事件不会乱序/重复编号；
 * 3. 发送单个事件失败（客户端断开）只记一次，不打断任务——断线不代表取消；
 * 4. 推送在调用线程内完成，避免额外队列带来的乱序；SseEmitter 自身是线程安全的。
 */
public class SseTaskProgressPublisher implements TaskProgressPublisher {

    private final Long planId;
    private final AtomicLong sequence = new AtomicLong();
    private final BiConsumer<String, Object> sender;

    public SseTaskProgressPublisher(Long planId, BiConsumer<String, Object> sender) {
        this.planId = planId;
        this.sender = sender;
    }

    @Override
    public void publish(AgentProgressEvent event) {
        if (event == null || sender == null) {
            return;
        }
        AgentProgressEvent ordered = new AgentProgressEvent(
                event.event(),
                event.planId() == null ? planId : event.planId(),
                event.runId(),
                event.nodeKey(),
                event.agentType(),
                sequence.incrementAndGet(),
                event.status(),
                event.summary(),
                event.timestamp() <= 0 ? System.currentTimeMillis() : event.timestamp()
        );
        try {
            sender.accept(ordered.event(), ordered);
        } catch (Exception ignored) {
            // 客户端断开：任务继续执行，用户可通过状态接口恢复
        }
    }
}
