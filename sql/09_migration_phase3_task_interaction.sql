-- =====================================================================
-- 第三阶段：任务交互（等待输入的待办问题 + 取消状态 + 父预算用量）
-- 说明：plan 保存等待用户补充的具体问题（前端刷新恢复展示）、取消时间，
--       以及父任务已用的模型请求/业务工具额度（补参恢复不得无界清零）。
--       AgentPlanStatus 增加 CANCELLED（代码枚举，无需改列）。
-- =====================================================================

ALTER TABLE agent_plan
    ADD COLUMN pending_question varchar(500) NULL COMMENT '等待用户补充时的具体问题（第三阶段）';

ALTER TABLE agent_plan
    ADD COLUMN cancelled_at datetime NULL COMMENT '用户显式取消时间（第三阶段）';

ALTER TABLE agent_plan
    ADD COLUMN model_calls_used int NOT NULL DEFAULT 0 COMMENT '父任务已用模型请求次数（第三阶段）';

ALTER TABLE agent_plan
    ADD COLUMN tool_calls_used int NOT NULL DEFAULT 0 COMMENT '父任务已用业务工具次数（第三阶段）';
