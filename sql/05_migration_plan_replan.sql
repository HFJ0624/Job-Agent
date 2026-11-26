-- =====================================================================
-- 企业级对标 G2：Replan 重规划计数
-- 对应：agent_plan 增加 replan_count，记录该计划已发生多少次“失败后重规划”，
--       用于有界重试（防止无限 replan）与后台审计。
-- 说明：专项迁移脚本；完整 Schema 基线版本化（Flyway）属于路线图任务4。
-- =====================================================================

ALTER TABLE agent_plan
    ADD COLUMN replan_count int NOT NULL DEFAULT 0 COMMENT '失败后重规划次数（有界，防止无限 replan）' AFTER fail_reason;

-- 回滚脚本（如需）：
-- ALTER TABLE agent_plan DROP COLUMN replan_count;
