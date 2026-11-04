package com.job.bootstrap.agent.orchestration.task;

import com.job.bootstrap.agent.infrastructure.persistence.AgentArtifactService;
import com.job.bootstrap.agent.core.budget.TaskBudgetRegistry;
import com.job.bootstrap.agent.core.budget.TaskExecutionBudget;
import com.job.bootstrap.agent.infrastructure.persistence.AgentRunService;
import com.job.common.entity.agent.AgentArtifact;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 任务状态快照（第三阶段：页面刷新/断线恢复）。
 *
 * 设计说明:
 * 1. 只返回“恢复界面所需”的最小信息：父任务状态、待补充问题、子节点状态、产物清单；
 * 2. 必须校验归属（loadOwned），不泄露他人任务；
 * 3. 不实现事件回放平台——快照足够恢复 WAITING_INPUT/RUNNING/FAILED 展示。
 */
@Service
@RequiredArgsConstructor
public class AgentTaskStatusService {

    private final AgentTaskService agentTaskService;
    private final AgentRunService agentRunService;
    private final AgentArtifactService agentArtifactService;
    private final TaskBudgetRegistry taskBudgetRegistry;

    /**
     * 查询任务快照。
     *
     * @return 快照；任务不存在或不属于当前用户返回 null
     */
    public Map<String, Object> snapshot(Long userId, Long planId) {
        AgentPlan plan = agentTaskService.loadOwned(userId, planId);
        if (plan == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("planId", plan.getId());
        snapshot.put("status", plan.getStatus());
        snapshot.put("runtimeMode", plan.getRuntimeMode());
        snapshot.put("pendingQuestion", plan.getPendingQuestion());
        snapshot.put("failReason", plan.getFailReason());
        snapshot.put("cancelledAt", plan.getCancelledAt());
        snapshot.put("updatedAt", plan.getUpdateTime());

        List<Map<String, Object>> runs = agentRunService.listByPlan(planId).stream()
                .map(this::runView).toList();
        snapshot.put("runs", runs);

        List<Map<String, Object>> artifacts = agentArtifactService.listByPlan(userId, planId, null).stream()
                .map(this::artifactView).toList();
        snapshot.put("artifacts", artifacts);

        TaskExecutionBudget budget = taskBudgetRegistry.find(planId);
        if (budget != null) {
            Map<String, Object> budgetView = new LinkedHashMap<>();
            budgetView.put("modelCallsUsed", budget.modelCallsUsed());
            budgetView.put("toolCallsUsed", budget.toolCallsUsed());
            budgetView.put("cancelled", budget.isCancelled());
            snapshot.put("budget", budgetView);
        }
        return snapshot;
    }

    private Map<String, Object> runView(AgentRun run) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runId", run.getId());
        view.put("nodeKey", run.getNodeKey());
        view.put("agentType", run.getAgentType());
        view.put("status", run.getStatus());
        view.put("attempt", run.getAttempt());
        view.put("errorMsg", run.getErrorMsg());
        view.put("updatedAt", run.getUpdateTime());
        return view;
    }

    private Map<String, Object> artifactView(AgentArtifact artifact) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("artifactId", artifact.getId());
        view.put("type", artifact.getType());
        view.put("inputVersion", artifact.getInputVersion());
        view.put("createdAt", artifact.getCreateTime());
        return view;
    }
}
