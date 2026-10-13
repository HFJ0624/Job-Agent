# sql/ 数据库脚本目录

本目录存放数据库 Schema 版本化相关脚本（路线图任务 4）。

## 当前状态
- 已接入 Flyway（见 `Job-Agent-Bootstrap/pom.xml` 的 flyway 依赖与 `application-dev.yml` 的 `spring.flyway` 配置）。
- 迁移脚本统一放在 `Job-Agent-Bootstrap/src/main/resources/db/migration/`，命名规范 `V{版本}__描述.sql`。

## 待办：生成 V1__baseline.sql（需在能连库的环境执行）
现有库已有约 30 张表但此前无版本管理。需先导出一份完整基线纳入 Flyway：

1. 导出结构（不含数据）：
   ```bash
   mysqldump -h 192.168.10.100 -P 3306 -u root -p --no-data \
     --skip-add-locks --skip-comments --databases job_agent \
     > Job-Agent-Bootstrap/src/main/resources/db/migration/V1__baseline.sql
   ```
   （pgvector 侧 RAG 表在 PostgreSQL，另行整理为单独脚本。）
2. 清理导出文件：去掉与 Flyway 冲突的条件注释、去掉 `CREATE DATABASE` / `USE`（schema 由 Flyway 管理）。
3. 放入上述 `V1__baseline.sql`。
4. 配置已设 `baseline-on-migrate=true, baseline-version=1`：
   - 现有库首次启动会自动打基线（跳过 V1，不重复建表）；
   - 全新库会执行 `V1__baseline.sql` 从零重建。

## 历史脚本
- `sql/01_migration_workflow_email_idempotency.sql`：任务 7 的邮件幂等迁移。
  其变更（`job_application_record.interview_email_sent_at`）将包含在 `V1__baseline.sql` 中；
  保留作历史记录。Flyway 的 locations 只指向 `db/migration`，不会执行本目录。

## 后续规范
- 所有加表 / 加字段 / 加索引，一律新增 `db/migration/V{n}__desc.sql`，不再手改库。
