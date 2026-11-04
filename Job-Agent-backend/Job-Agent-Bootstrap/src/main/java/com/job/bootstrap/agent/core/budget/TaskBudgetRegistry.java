package com.job.bootstrap.agent.core.budget;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能: 父任务预算注册表（第三阶段）。
 *
 * 用途:
 * 1. 取消接口按 planId 找到运行中的预算并置取消信号；
 * 2. 状态接口读取已用额度/是否取消；
 * 3. 单实例内存表即可（个人项目单机部署），任务结束保留短时间用于状态查询，避免无限增长。
 */
@org.springframework.stereotype.Component
public class TaskBudgetRegistry {

    private final Map<Long, TaskExecutionBudget> budgets = new ConcurrentHashMap<>();

    public TaskExecutionBudget getOrCreate(Long planId) {
        return budgets.computeIfAbsent(planId, TaskExecutionBudget::new);
    }

    public TaskExecutionBudget find(Long planId) {
        return planId == null ? null : budgets.get(planId);
    }

    /** 取消指定任务（不存在返回 false）。 */
    public boolean cancel(Long planId, String reason) {
        TaskExecutionBudget budget = find(planId);
        if (budget == null) {
            return false;
        }
        budget.cancel(reason);
        return true;
    }

    /** 任务占用结束后释放（状态查询仍可从计划表读取）。 */
    public void release(Long planId) {
        if (planId != null) {
            budgets.remove(planId);
        }
    }
}
