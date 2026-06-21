package com.job.bootstrap.rag.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

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
     * 创建 RAG 专用 JdbcTemplate。
     *
     * @return 只连接 pgvector 库的 JdbcTemplate
     */
    @Bean("ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate() {
        //1.获取pgvector数据库的专用配置节点和pgvector数据源的详细配置
        RagProperties.Pgvector pgvector = ragProperties.getPgvector();
        RagProperties.DataSource datasourceProperties = pgvector.getDatasource();

        //2.使用DriverManagerDataSource创建临时数据源（非连接池实现）
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(defaultIfBlank(
                datasourceProperties.getDriverClassName(),
                "org.postgresql.Driver"
        ));

        //3.配置数据库用户名：优先使用datasource显式配置，未配置则使用pgvector全局用户名
        dataSource.setUrl(resolveJdbcUrl(pgvector, datasourceProperties));
        dataSource.setUsername(defaultIfBlank(datasourceProperties.getUsername(), pgvector.getUser()));
        dataSource.setPassword(defaultIfBlank(datasourceProperties.getPassword(), ""));

        //4.返回基于pgvector专用数据源创建JdbcTemplate实例
        return new JdbcTemplate(dataSource);
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
     * @param value 待检查的字符串值
     * @param defaultValue 字符串为空时的默认值
     * @return 简化的字符串空值处理工具方法
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
