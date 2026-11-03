package com.job.bootstrap.agent.task;

/**
 * 功能: 父任务预算的线程上下文（第三阶段收口）。
 *
 * 为什么需要: Runner 外层每次 chatTurn 扣一次额度，但网关内部还会重试/切备用模型——
 * 这些真实 HTTP 尝试也必须受同一父截止时间与请求额度约束，否则"预算"只是假象。
 *
 * 用法: Runner 在调用网关前 set，finally clear；网关实现读取后决定是否继续重试，
 * 并把每次真实尝试计入额度（tryReserveModelCall）。
 */
public final class TaskBudgetContext {

    private static final ThreadLocal<TaskExecutionBudget> HOLDER = new ThreadLocal<>();

    private TaskBudgetContext() {
    }

    public static void set(TaskExecutionBudget budget) {
        if (budget == null) {
            HOLDER.remove();
            return;
        }
        HOLDER.set(budget);
    }

    public static TaskExecutionBudget get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
