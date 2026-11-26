-- =====================================================================
-- 企业级对标 G1：模型分级路由（tier routing）
-- 对应：ai_model_route 增加 tier 档位，网关按 sceneCode + tier 解析路由，
--       同一场景可给不同难度任务配不同模型；未配 tier 的老路由按 STANDARD 处理。
-- 说明：专项迁移脚本；完整 Schema 基线版本化（Flyway）属于路线图任务4。
-- =====================================================================

ALTER TABLE ai_model_route
    ADD COLUMN tier varchar(16) NOT NULL DEFAULT 'STANDARD' COMMENT '模型档位：LIGHT轻量/STANDARD标准/PRO强模型' AFTER ab_group;

-- 回滚脚本（如需）：
-- ALTER TABLE ai_model_route DROP COLUMN tier;
