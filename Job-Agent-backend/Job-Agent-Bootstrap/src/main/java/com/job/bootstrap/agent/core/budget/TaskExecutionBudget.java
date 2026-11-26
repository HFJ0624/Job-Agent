package com.job.bootstrap.agent.core.budget;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 功能: 父任务执行预算（第三阶段）。
 *
 * 设计说明:
 * 1. 一个父任务**只有一份**预算：绝对截止时间 + 模型请求额度 + 业务工具额度 + 取消信号；
 * 2. 并行节点必须**先原子占用额度再调用**（compareAndSet 递减），不能先执行再统计；
 * 3. 子节点的局部上限（每 run 轮次/工具次数）继续保留，但每次实际调用都要再过父预算这一关；
 * 4. 等待用户补充的时间不算模型耗时：补参恢复时按规则重置截止窗口，但**额度不清零**（见 restoreWindow）。
 */
public class TaskExecutionBudget {

    /** 父任务总时限（毫秒）——配置初值，可按需调整。 */
    public static final long DEFAULT_DEADLINE_MILLIS = 300_000L;
    /** 模型实际请求上限（含重试/备用模型）。 */
    public static final int DEFAULT_MAX_MODEL_CALLS = 24;
    /** 业务工具调用上限。 */
    public static final int DEFAULT_MAX_TOOL_CALLS = 30;
    /** 父任务内模型并发上限。 */
    public static final int DEFAULT_MAX_CONCURRENCY = 2;

    private final Long planId;
    private final long deadlineMillis;
    private final int maxModelCalls;
    private final int maxToolCalls;
    private final int maxConcurrency;

    private volatile long deadlineAtMillis;
    private final AtomicInteger modelCallsUsed = new AtomicInteger();
    private final AtomicInteger toolCallsUsed = new AtomicInteger();
    private final AtomicInteger inFlightModelCalls = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String cancelReason;

    public TaskExecutionBudget(Long planId) {
        this(planId, DEFAULT_DEADLINE_MILLIS, DEFAULT_MAX_MODEL_CALLS, DEFAULT_MAX_TOOL_CALLS, DEFAULT_MAX_CONCURRENCY);
    }

    public TaskExecutionBudget(Long planId, long deadlineMillis, int maxModelCalls, int maxToolCalls, int maxConcurrency) {
        this.planId = planId;
        this.deadlineMillis = deadlineMillis;
        this.maxModelCalls = maxModelCalls;
        this.maxToolCalls = maxToolCalls;
        this.maxConcurrency = maxConcurrency;
        this.deadlineAtMillis = System.currentTimeMillis() + deadlineMillis;
    }

    /** 以持久化状态恢复（已用额度延续，截止时间按 restoreWindow 处理）。 */
    public void restore(int modelCallsUsed, int toolCallsUsed, long usedMillis) {
        this.modelCallsUsed.set(Math.max(0, modelCallsUsed));
        this.toolCallsUsed.set(Math.max(0, toolCallsUsed));
        restoreWindow(usedMillis);
    }

    /**
     * 重置活动时间窗口（补参恢复用）：用户等待时间不计入执行耗时，但额度不清零。
     */
    public void restoreWindow(long usedMillis) {
        long remaining = Math.max(1_000L, deadlineMillis - Math.max(0L, usedMillis));
        this.deadlineAtMillis = System.currentTimeMillis() + remaining;
    }

    /** 原子占用一次模型请求额度；额度不足/已取消/超时返回 false。 */
    public boolean tryReserveModelCall() {
        if (!canContinue()) {
            return false;
        }
        while (true) {
            int used = modelCallsUsed.get();
            if (used >= maxModelCalls) {
                return false;
            }
            if (modelCallsUsed.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }

    /** 原子占用一次业务工具调用额度。 */
    public boolean tryReserveToolCall() {
        if (!canContinue()) {
            return false;
        }
        while (true) {
            int used = toolCallsUsed.get();
            if (used >= maxToolCalls) {
                return false;
            }
            if (toolCallsUsed.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }

    /** 模型并发闸门（并行节点用）。 */
    public boolean tryEnterModelCall() {
        while (true) {
            int current = inFlightModelCalls.get();
            if (current >= maxConcurrency) {
                return false;
            }
            if (inFlightModelCalls.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void exitModelCall() {
        inFlightModelCalls.updateAndGet(current -> Math.max(0, current - 1));
    }

    public boolean canContinue() {
        return !cancelled.get() && !deadlineExceeded();
    }

    public boolean deadlineExceeded() {
        return System.currentTimeMillis() >= deadlineAtMillis;
    }

    public long remainingMillis() {
        return Math.max(0L, deadlineAtMillis - System.currentTimeMillis());
    }

    public long usedMillis() {
        return Math.max(0L, deadlineMillis - remainingMillis());
    }

    public void cancel(String reason) {
        this.cancelReason = reason;
        this.cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public String cancelReason() {
        return cancelReason;
    }

    public Long planId() {
        return planId;
    }

    public int modelCallsUsed() {
        return modelCallsUsed.get();
    }

    public int toolCallsUsed() {
        return toolCallsUsed.get();
    }

    public int maxModelCalls() {
        return maxModelCalls;
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }
}
