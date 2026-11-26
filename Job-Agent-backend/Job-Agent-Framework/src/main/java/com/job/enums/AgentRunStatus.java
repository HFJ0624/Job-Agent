package com.job.enums;

/**
 * 功能: Agent run 状态（第一阶段 ReAct）。
 *
 * 说明:
 * 1. PENDING → RUNNING → SUCCEEDED / FAILED；需要补参时 RUNNING → WAITING_INPUT → RUNNING（续跑）；
 * 2. 状态迁移只允许通过条件更新完成（原子领取/续跑），防止并发重复执行；
 * 3. 本枚举只表达“运行状态”，业务结论（FOUND/EMPTY/NEED_INPUT/FAILED）保存在 result_json 里。
 */
public enum AgentRunStatus {

    /** 已创建，等待领取。 */
    PENDING,

    /** 执行中。 */
    RUNNING,

    /** 等待用户补充输入（补参后续跑同一 run）。 */
    WAITING_INPUT,

    /** 执行成功（业务结论可能 FOUND 或 EMPTY）。 */
    SUCCEEDED,

    /** 执行失败（模型/工具/预算/协议）。 */
    FAILED
}
