-- =====================================================================
-- 第三阶段收口（审查 F1/F2/F5）：
-- 1. final_answer：完成任务的重复提交直接返回已保存答复；
-- 2. execution_millis_used：补参恢复延续已消耗执行时间（等待用户不计时）。
-- =====================================================================

ALTER TABLE agent_plan
    ADD COLUMN final_answer text NULL COMMENT '任务完成时的最终答复（重复提交复用）';

ALTER TABLE agent_plan
    ADD COLUMN execution_millis_used int NOT NULL DEFAULT 0 COMMENT '已消耗执行耗时（毫秒，补参恢复延续）';
