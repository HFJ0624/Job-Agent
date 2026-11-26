package com.job.bootstrap.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 功能: 主数据源（MySQL）显式声明（修复启动失败）。
 *
 * 背景:
 * 1. {@link com.job.bootstrap.rag.config.RagConfig} 声明了 pgvector 的 DataSource（ragDataSource），
 *    它同样是 DataSource 类型——Spring Boot 的 DataSource 自动配置看到已存在 DataSource 就退让，
 *    导致上下文里只剩 PG 数据源：MyBatis 与 Flyway 都指向 PostgreSQL，
 *    Flyway 于是报 "Unsupported Database: PostgreSQL 18.3"，应用无法启动；
 * 2. 这里按 spring.datasource.* 显式创建主数据源并标记 @Primary：
 *    MyBatis-Plus、Flyway、事务都回到业务库（MySQL），RAG 仍用 @Qualifier("ragDataSource") 的 PG 池。
 *
 * 说明:
 * 1. 不使用 @ConfigurationProperties 绑定，是因为配置写的是 spring.datasource.url（非 hikari 前缀），
 *    直接读取 Environment 最直观，也避免与 spring.datasource.type 冲突；
 * 2. 业务库连接信息仍由 application-dev.yml 的环境变量注入（不在仓库保存明文口令）。
 */
@Slf4j
@Configuration
public class PrimaryDataSourceConfig {

    /**
     * 主数据源（MySQL）：@Primary 保证 Flyway / MyBatis / 事务默认选中它。
     */
    @Bean(name = "dataSource", destroyMethod = "close")
    @Primary
    public HikariDataSource dataSource(Environment environment) {
        String url = environment.getProperty("spring.datasource.url");
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("缺少 spring.datasource.url，无法创建主数据源");
        }

        HikariConfig config = new HikariConfig();
        config.setPoolName("job-agent-primary-pool");
        config.setJdbcUrl(url);
        config.setUsername(environment.getProperty("spring.datasource.username", "root"));
        config.setPassword(environment.getProperty("spring.datasource.password", ""));
        config.setDriverClassName(environment.getProperty(
                "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"));

        Integer maxPoolSize = environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
        if (maxPoolSize != null) {
            config.setMaximumPoolSize(maxPoolSize);
        }
        Integer minIdle = environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class);
        if (minIdle != null) {
            config.setMinimumIdle(minIdle);
        }

        // 启动时只校验配置，不强制建连：数据库暂时不可达时应用仍能启动并暴露健康检查
        config.setInitializationFailTimeout(-1);
        log.info("主数据源初始化：{}", maskUrl(url));
        return new HikariDataSource(config);
    }

    // 日志中隐去账号信息，只保留 host/db 便于排查
    private String maskUrl(String url) {
        int at = url.indexOf('@');
        return at > 0 ? "jdbc:***@" + url.substring(at + 1) : url;
    }
}
