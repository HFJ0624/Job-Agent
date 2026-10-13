package com.job.bootstrap.rag.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 作者:hfj
 * 功能:RAG 基础设施配置
 * 日期:2026/6/14
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    private final RagProperties ragProperties;

    /**
     * 创建 RAG 专用 pgvector 数据源（HikariCP 连接池）。
     *
     * <p>原实现使用 DriverManagerDataSource（每次新建连接、无池化），
     * 在高并发检索下会频繁建连。改为 HikariCP，复用连接、提升检索稳定性。</p>
     *
     * @return 只连接 pgvector 库的连接池数据源
     */
    @Bean(name = "ragDataSource", destroyMethod = "close")
    public HikariDataSource ragDataSource() {
        //1.获取pgvector数据库的专用配置节点和pgvector数据源的详细配置
        RagProperties.Pgvector pgvector = ragProperties.getPgvector();
        RagProperties.DataSource datasourceProperties = pgvector.getDatasource();

        //2.使用 HikariCP 连接池（替代原来的 DriverManagerDataSource 非池化实现）
        HikariConfig config = new HikariConfig();
        config.setPoolName("rag-pgvector-pool");
        config.setJdbcUrl(resolveJdbcUrl(pgvector, datasourceProperties));
        config.setUsername(defaultIfBlank(datasourceProperties.getUsername(), pgvector.getUser()));
        config.setPassword(defaultIfBlank(datasourceProperties.getPassword(), ""));
        config.setDriverClassName(defaultIfBlank(
                datasourceProperties.getDriverClassName(),
                "org.postgresql.Driver"
        ));
        config.setMaximumPoolSize(datasourceProperties.getMaximumPoolSize());
        config.setMinimumIdle(datasourceProperties.getMinimumIdle());

        return new HikariDataSource(config);
    }

    /**
     * 创建 RAG 专用 JdbcTemplate。
     *
     * @param ragDataSource pgvector 连接池数据源
     * @return 只连接 pgvector 库的 JdbcTemplate
     */
    @Bean("ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }

    /***
     *
     * @return 解析pgvector数据库的JDBC连接URL
     */
    private String resolveJdbcUrl(RagProperties.Pgvector pgvector, RagProperties.DataSource datasourceProperties) {
        if (StringUtils.hasText(datasourceProperties.getJdbcUrl())) {
            return datasourceProperties.getJdbcUrl();
        }
        if (!StringUtils.hasText(pgvector.getHost()) || !StringUtils.hasText(pgvector.getDatabase())) {
            throw new IllegalStateException("请配置 job.rag.pgvector.datasource.jdbc-url 或 host/database");
        }
        return "jdbc:postgresql://" + pgvector.getHost() + ":" + pgvector.getPort() + "/" + pgvector.getDatabase();
    }

    /***
     *
     * @param value 待检查的字符串
     * @param defaultValue 字符串为空时的默认值
     * @return 简化的字符串空值处理工具方法
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
