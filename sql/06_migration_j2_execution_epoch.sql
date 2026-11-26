-- =====================================================================
-- J2：执行批次号（execution_epoch）与步骤观测字段
-- 对应：AgentPlanExecutorServiceImpl 的持有权校验——
--       抢占时 epoch+1，所有步骤回写带 "计划 epoch 仍属本批次" 条件，
--       旧执行器的写入影响 0 行，结果被丢弃，不会覆盖新执行器。
-- 说明：专项迁移脚本；完整 Schema 基线版本化（Flyway）属于路线图任务4。
-- =====================================================================

ALTER TABLE agent_plan
    ADD COLUMN execution_epoch int NOT NULL DEFAULT 0 COMMENT '执行批次号：每次成功抢占+1，步骤回写校验持有权（任务 J2）';

ALTER TABLE agent_plan_step
    ADD COLUMN attempt_count int NOT NULL DEFAULT 0 COMMENT '尝试次数：每次置 RUNNING +1（任务 J2）';

ALTER TABLE agent_plan_step
    ADD COLUMN started_at datetime NULL COMMENT '本次执行开始时间（任务 J2）';

ALTER TABLE agent_plan_step
    ADD COLUMN finished_at datetime NULL COMMENT '本次执行结束时间（任务 J2）';

-- 回滚脚本（如需）：
-- ALTER TABLE agent_plan DROP COLUMN execution_epoch;
-- ALTER TABLE agent_plan_step DROP COLUMN attempt_count;
-- ALTER TABLE agent_plan_step DROP COLUMN started_at;
-- ALTER TABLE agent_plan_step DROP COLUMN finished_at;
