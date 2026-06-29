package com.job.enums;

/**
 * 第一版支持的工作流任务类型。
 */
public enum WorkflowTaskType {

    RAG_REBUILD_ALL,

    RAG_REBUILD_USER,

    AGENT_EVAL_RUN_DATASET,

    /**
     * 已约面试后，异步给用户发送面试通知邮件。
     */
    INTERVIEW_EMAIL_NOTIFY
}
