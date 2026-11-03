-- =====================================================================
-- 第二阶段：三 Agent 协作与 Workflow 编排
-- 说明：
-- 1. agent_run 增加节点标识，支持一个父计划下多个子 run（不能再用 findLatestByPlan 猜）；
-- 2. 新增 agent_artifact 保存结构化产物（ResumeProfile / JobCandidates / MatchReport），
--    完整 JSON 单独保存（摘要不参与程序恢复）；
-- 3. agent_plan.runtime_mode 已在 sql/07 增加，此处仅补充索引。
-- =====================================================================

ALTER TABLE agent_run
    ADD COLUMN node_key varchar(64) NULL COMMENT '节点标识，如 RESUME_AGENT / JOB_SEARCH_AGENT / MATCH_AGENT';

ALTER TABLE agent_run
    ADD COLUMN agent_type varchar(32) NULL COMMENT 'Agent 类型：RESUME / JOB_SEARCH / MATCH';

ALTER TABLE agent_run
    ADD COLUMN input_version varchar(64) NULL COMMENT '输入版本（条件版本/上游产物版本），用于定位逻辑节点';

CREATE INDEX idx_agent_run_node ON agent_run (plan_id, node_key, input_version);

CREATE TABLE IF NOT EXISTS agent_artifact (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '用户ID（归属校验）',
    plan_id        BIGINT       NULL COMMENT '父计划ID',
    run_id         BIGINT       NULL COMMENT '产出该产物的子 run ID',
    type           VARCHAR(32)  NOT NULL COMMENT '产物类型：RESUME_PROFILE / JOB_CANDIDATES / MATCH_REPORT',
    schema_version VARCHAR(16)  NOT NULL COMMENT '产物 Schema 版本，如 v1',
    input_version  VARCHAR(64)  NULL COMMENT '输入版本/条件版本（版本不一致即失效）',
    content_json   MEDIUMTEXT   NOT NULL COMMENT '完整产物 JSON（不做摘要截断）',
    source_refs    TEXT         NULL COMMENT '来源引用（简历/岗位/上游产物 ID）',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    update_time    DATETIME     NOT NULL COMMENT '更新时间',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_artifact_plan_type (plan_id, type, input_version),
    KEY idx_artifact_user (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Agent 结构化产物（第二阶段）';
