package com.job.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 作者:hfj
 * 功能:Job-Agent 后端项目启动入口
 * 日期:2026/6/2 10:45
 */
@EnableScheduling
/*
 * 排除 Flyway 自动配置（启动修复）：
 * 1. 任务4 只搭了脚手架，基线脚本尚未落地（src/main/resources/db/migration 为空）；
 * 2. 应用里存在 RAG 的 pgvector DataSource，Flyway 曾误连 PostgreSQL 直接导致启动失败；
 * 3. 业务库结构一直由 sql/ 手工脚本维护，因此默认不启用迁移。
 *
 * 将来启用：删除 exclude，在 db/migration 放入 V1__baseline.sql，并设置 FLYWAY_ENABLED=true。
 */
@SpringBootApplication(scanBasePackages = "com.job", exclude = FlywayAutoConfiguration.class)
public class JobAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAgentApplication.class, args);
    }
}
