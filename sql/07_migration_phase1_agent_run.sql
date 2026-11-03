-- =====================================================================
-- 第一阶段：ReAct + Workflow（JobSearchAgent）
-- 说明：新增 agent_run 表承载单次 Agent run 的状态/消息/结果；
--       agent_plan 增加 runtime_mode，保证“已有计划按创建时固定的引擎继续执行”。
-- =====================================================================

CREATE TABLE IF NOT EXISTS agent_run (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    conversation_id   BIGINT       NOT NULL COMMENT '会话ID',
    plan_id           BIGINT       NULL COMMENT '关联计划ID（复用现有计划/会话）',
    trace_id          VARCHAR(64)  NOT NULL COMMENT '链路ID',
    agent_name        VARCHAR(64)  NOT NULL COMMENT 'Agent 名称，如 job-search',
    status            VARCHAR(32)  NOT NULL COMMENT 'PENDING/RUNNING/WAITING_INPUT/SUCCEEDED/FAILED',
    input_text        TEXT         NOT NULL COMMENT '本轮用户输入',
    constraints_json  TEXT         NULL COMMENT 'SearchConstraints 快照（含来源标记）',
    messages_json     MEDIUMTEXT   NULL COMMENT '完整消息序列（含 tool_calls/tool 结果，恢复用）',
    result_json       TEXT         NULL COMMENT '最终结果（outcome/候选人/缺失字段/原因码）',
    error_msg         VARCHAR(1000) NULL COMMENT '失败原因',
    attempt           INT          NOT NULL DEFAULT 0 COMMENT '领取次数（防旧执行器晚回写）',
    version           INT          NOT NULL DEFAULT 0 COMMENT '版本号（每次状态变更+1）',
    create_time       DATETIME     NOT NULL COMMENT '创建时间',
    update_time       DATETIME     NOT NULL COMMENT '更新时间',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_agent_run_user (user_id, create_time),
    KEY idx_agent_run_status (status, update_time),
    KEY idx_agent_run_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent run 状态表（第一阶段 ReAct）';

ALTER TABLE agent_plan
    ADD COLUMN runtime_mode VARCHAR(32) NULL COMMENT '执行引擎：WORKFLOW_REACT/TEMPLATE（创建时固定，任务第一阶段）';
