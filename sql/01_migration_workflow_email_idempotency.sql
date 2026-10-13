-- =====================================================================
-- 阶段任务 7：面试通知邮件幂等（Workflow 两个 P0 之一）
-- 对应：InterviewEmailNotifyWorkflowTaskHandler 原来每次执行都发信，
--       任务被 markFailure 重试 / 重复执行时会重复发送“已约面试”邮件。
-- 方案：业务状态位 interview_email_sent_at（NULL=未发送，非空=已发送），
--       通过原子条件更新抢占发送权，保证同一求职记录只发一封邮件。
-- 说明：这是专项迁移脚本；完整 Schema 基线版本化（sql/01_schema_*.sql + Flyway）
--       属于路线图任务 4，尚未实施。
-- =====================================================================

ALTER TABLE job_application_record
    ADD COLUMN interview_email_sent_at datetime NULL COMMENT '面试通知邮件已发送时间（幂等状态位，NULL=未发送）' AFTER interview_time;

-- 回滚脚本（如需）：
-- ALTER TABLE job_application_record DROP COLUMN interview_email_sent_at;
