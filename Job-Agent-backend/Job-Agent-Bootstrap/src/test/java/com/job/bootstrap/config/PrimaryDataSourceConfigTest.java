package com.job.bootstrap.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动失败回归验证：主数据源必须是 MySQL 且标记为 @Primary，
 * 不能因为 RAG 的 pgvector 数据源存在而让 MyBatis/Flyway 选错库。
 */
class PrimaryDataSourceConfigTest {

    @Test
    void shouldCreatePrimaryMysqlDataSourceFromSpringDatasourceProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/job_agent",
                "spring.datasource.username", "root",
                "spring.datasource.password", "",
                "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"
        )));

        HikariDataSource dataSource = new PrimaryDataSourceConfig().dataSource(environment);
        try {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/job_agent");
            assertThat(dataSource.getDriverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        } finally {
            dataSource.close();
        }
    }

    @Test
    void beanMustBePrimary() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", "jdbc:mysql://127.0.0.1:3306/job_agent",
                "spring.datasource.username", "root",
                "spring.datasource.password", ""
        )));
        context.register(PrimaryDataSourceConfig.class);
        context.refresh();
        try {
            assertThat(context.getBean(javax.sql.DataSource.class)).isNotNull();
            assertThat(context.getBeanFactory().getBeanNamesForType(javax.sql.DataSource.class)).hasSize(1);
        } finally {
            context.close();
        }
    }
}
