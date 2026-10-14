-- =====================================================================
-- 阶段任务6：Context Builder 滚动摘要缓存
-- 对应：多轮上下文超预算时，把“更早的历史”压缩成一段摘要缓存到会话上，
--       总结时用「滚动摘要 + 最近 N 轮原文」控制 Prompt 大小。
-- 说明：这是专项迁移脚本；完整 Schema 基线版本化（Flyway）属于路线图任务4。
-- =====================================================================

ALTER TABLE ai_conversation
    ADD COLUMN summary TEXT NULL COMMENT '更早历史的滚动摘要（CONTEXT_SUMMARY 场景生成）' AFTER title,
    ADD COLUMN summary_upto_message_id BIGINT NULL COMMENT '摘要已覆盖到的最大消息ID（用于增量滚动，避免重复压缩）' AFTER summary;

-- 回滚脚本（如需）：
-- ALTER TABLE ai_conversation DROP COLUMN summary, DROP COLUMN summary_upto_message_id;
