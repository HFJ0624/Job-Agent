package com.job.bootstrap.agent.core.react;

import com.job.bootstrap.agent.core.budget.TaskExecutionBudget;
import com.job.bootstrap.ai.turn.AiChatMessage;

import java.util.List;

/**
 * 功能: 单次 run 的执行钩子（第一阶段验收修复 R5/R6）。
 *
 * 设计说明:
 * 1. Runner 的构造器被既有测试与 Workflow 依赖固定，新增参数会破坏兼容——
 *    这里用 ThreadLocal 传递“绝对截止时间 + 检查点回调”，由入口设置、Runner 读取、finally 清理；
 * 2. 截止时间：不但每轮开始检查，模型返回后、工具执行前后、终结结果接受前都要检查
 *    （迟到的成功结果同样不能接受）；
 * 3. 检查点：每轮工具结果落地后回调一次，业务侧据此做短事务保存——
 *    进程中途退出不会只剩 RUNNING 而没有检查点。
 */
public final class AgentRunExecutionHooks {

    private static final ThreadLocal<Hooks> HOLDER = new ThreadLocal<>();

    private AgentRunExecutionHooks() {
    }

    /**
     * @param deadlineAtMillis 绝对截止时间戳（null = 不限制）
     * @param checkpoint       消息检查点回调（null = 不保存）
     * @param budget           父任务共享预算（null = 不限制；第三阶段）
     */
    public record Hooks(Long deadlineAtMillis, Checkpoint checkpoint, TaskExecutionBudget budget) {
    }

    @FunctionalInterface
    public interface Checkpoint {
        /**
         * 保存消息检查点（实现方负责短事务）。
         *
         * @param messages 当前完整消息序列快照
         */
        void save(List<AiChatMessage> messages);
    }

    public static void set(Long deadlineAtMillis, Checkpoint checkpoint) {
        HOLDER.set(new Hooks(deadlineAtMillis, checkpoint, null));
    }

    /** 第三阶段：带父任务预算的钩子（子节点继承同一份预算）。 */
    public static void set(Long deadlineAtMillis, Checkpoint checkpoint, TaskExecutionBudget budget) {
        HOLDER.set(new Hooks(deadlineAtMillis, checkpoint, budget));
    }

    /** 当前父任务预算（可空）。 */
    public static TaskExecutionBudget budget() {
        Hooks hooks = HOLDER.get();
        return hooks == null ? null : hooks.budget();
    }

    public static Hooks get() {
        return HOLDER.get();
    }

    /** 是否已超过绝对截止时间（含父预算截止）。 */
    public static boolean deadlineExceeded() {
        Hooks hooks = HOLDER.get();
        if (hooks == null) {
            return false;
        }
        if (hooks.deadlineAtMillis() != null && System.currentTimeMillis() >= hooks.deadlineAtMillis()) {
            return true;
        }
        return hooks.budget() != null && hooks.budget().deadlineExceeded();
    }

    /** 父任务是否已取消（第三阶段）。 */
    public static boolean cancelled() {
        TaskExecutionBudget budget = budget();
        return budget != null && budget.isCancelled();
    }

    /** 触发检查点（无回调时静默跳过）。 */
    public static void checkpoint(List<AiChatMessage> messages) {
        Hooks hooks = HOLDER.get();
        if (hooks != null && hooks.checkpoint() != null) {
            try {
                hooks.checkpoint().save(messages);
            } catch (Exception ignored) {
                // 检查点失败不能打断主流程（下一次检查点会覆盖）
            }
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
