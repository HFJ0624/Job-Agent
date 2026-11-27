package com.job.agent.application.task;

import com.job.agent.core.budget.TaskBudgetRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 功能: 任务取消用例（Controller 的唯一入口）。
 *
 * <p>顺序保持原语义：先校验任务归属（快照为空即无权/不存在）→ 取消内存预算信号
 * → 立即持久化 CANCELLED。等待补参时没有活动执行链，也能取消。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentTaskCancelService {

    private final AgentTaskStatusService agentTaskStatusService;
    private final AgentTaskService agentTaskService;
    private final TaskBudgetRegistry taskBudgetRegistry;

    /**
     * 取消任务。
     *
     * @return true=已受理；false=任务不存在或不属于该用户
     */
    public boolean cancel(Long userId, Long planId) {
        if (planId == null) {
            return false;
        }
        if (agentTaskStatusService.snapshot(userId, planId) == null) {
            return false;
        }
        taskBudgetRegistry.cancel(planId, "USER_CANCELLED");
        agentTaskService.markCancelled(planId, "USER_CANCELLED");
        return true;
    }
}
