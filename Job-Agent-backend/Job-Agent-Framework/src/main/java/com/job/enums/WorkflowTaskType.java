package com.job.enums;

/**
 * 支持的工作流任务类型。
 */
public enum WorkflowTaskType {

    RAG_REBUILD_ALL,

    RAG_REBUILD_USER,

    AGENT_EVAL_RUN_DATASET,

    /**
     * Agent Eval 批次异步执行（bizId = evalRunId）。
     */
    AGENT_EVAL_RUN,

    /**
     * 已约面试后，异步给用户发送面试通知邮件。
     */
    INTERVIEW_EMAIL_NOTIFY
}
