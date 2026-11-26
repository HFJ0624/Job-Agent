package com.job.bootstrap.agent.core.event;

/**
 * 功能: 任务进度事件（第三阶段）。
 *
 * 字段说明:
 * 1. planId = 父任务；runId = 当前子 Agent 执行；nodeKey/agentType 表示节点与角色；
 * 2. seq 在同一任务内单调递增（并行节点也安全），前端可按 seq 排序/去重；
 * 3. summary 只放“用户能看懂的动作/结果摘要”，不含工具原始参数、系统 Prompt、模型推理。
 *
 * @param event     事件名：task_started / node_started / node_completed / waiting_input / task_failed / task_completed
 * @param planId    父任务ID
 * @param runId     子 run ID（可空）
 * @param nodeKey   节点标识（可空）
 * @param agentType Agent 类型（RESUME/JOB_SEARCH/MATCH）
 * @param seq       任务内事件序号
 * @param status    状态摘要（RUNNING/SUCCEEDED/WAITING_INPUT/FAILED/...）
 * @param summary   可展示摘要
 * @param timestamp 毫秒时间戳
 */
public record AgentProgressEvent(
        String event,
        Long planId,
        Long runId,
        String nodeKey,
        String agentType,
        long seq,
        String status,
        String summary,
        long timestamp
) {

    public static final String TASK_STARTED = "task_started";
    public static final String NODE_STARTED = "node_started";
    public static final String NODE_COMPLETED = "node_completed";
    public static final String WAITING_INPUT = "waiting_input";
    public static final String TASK_FAILED = "task_failed";
    public static final String TASK_COMPLETED = "task_completed";
}
