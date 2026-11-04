package com.job.bootstrap.agent.runtime.progress;

/**
 * 功能: 任务进度发布器（第三阶段）。
 *
 * 设计说明:
 * 1. 以任务为范围（planId）创建，由 SSE 控制器注入，经入口 → Workflow → 节点显式传递；
 *    并行节点各自持有同一个 publisher 引用，实现类内部保证 seq 原子与线程安全；
 * 2. 不引入全局共享可变对话上下文，也不做跨用户广播；
 * 3. 同步 chat 使用 {@link #NOOP}，行为与之前完全一致。
 */
@FunctionalInterface
public interface TaskProgressPublisher {

    /** 空实现：同步接口/单测默认使用。 */
    TaskProgressPublisher NOOP = event -> {
    };

    /**
     * 发布事件（实现方需静默吞掉推送失败，不能打断任务执行）。
     */
    void publish(AgentProgressEvent event);
}
