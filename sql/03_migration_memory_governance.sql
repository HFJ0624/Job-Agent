-- =====================================================================
-- 阶段任务9：Memory 治理（TTL + 冲突待确认）
-- 对应：agent_long_term_memory 增加过期时间与冲突待确认标记。
-- 说明：专项迁移脚本；完整 Schema 基线版本化（Flyway）属于路线图任务4。
-- =====================================================================

ALTER TABLE agent_long_term_memory
    ADD COLUMN expire_time datetime NULL COMMENT '过期时间(TTL，NULL=不过期)，到达后由每日任务归档、不再参与召回' AFTER last_used_time,
    ADD COLUMN need_confirm tinyint NOT NULL DEFAULT 0 COMMENT '待确认冲突：1=低置信度来源尝试覆盖高置信度记忆被拒，保留旧值待人工确认' AFTER expire_time;

-- 回滚脚本（如需）：
-- ALTER TABLE agent_long_term_memory DROP COLUMN expire_time, DROP COLUMN need_confirm;
