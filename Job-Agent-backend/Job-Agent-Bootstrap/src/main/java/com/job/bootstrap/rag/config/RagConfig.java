package com.job.bootstrap.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

import java.time.Duration;

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
        RagProperties.Pgvector pgvector = ragProperties.getPgvector();
        RagProperties.DataSource datasourceProperties = pgvector.getDatasource();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(defaultIfBlank(
                datasourceProperties.getDriverClassName(),
                "org.postgresql.Driver"
        ));
        dataSource.setUrl(resolveJdbcUrl(pgvector, datasourceProperties));
        dataSource.setUsername(defaultIfBlank(datasourceProperties.getUsername(), pgvector.getUser()));
        dataSource.setPassword(defaultIfBlank(datasourceProperties.getPassword(), ""));

        /*
         * 注意:
         * 1. 这里没有声明 DataSource Bean，只声明 JdbcTemplate Bean。
         * 2. 这样不会影响 Spring Boot 为主业务库创建的 MySQL DataSource。
         * 3. MyBatis-Plus 仍然使用原来的 spring.datasource。
         */
        return new JdbcTemplate(dataSource);
    }

    /**
     * 创建 RAG 专用 EmbeddingModel。
     *
     * @return OpenAI 兼容 Embedding 模型
     */
    @Bean("ragEmbeddingModel")
    public EmbeddingModel ragEmbeddingModel() {
        RagProperties.Embedding embedding = ragProperties.getEmbedding();

        if (!StringUtils.hasText(embedding.getApiKey())) {
            throw new IllegalStateException("请配置 job.rag.embedding.api-key");
        }
        if (!StringUtils.hasText(embedding.getBaseUrl())) {
            throw new IllegalStateException("请配置 job.rag.embedding.base-url");
        }
        if (!StringUtils.hasText(embedding.getModelName())) {
            throw new IllegalStateException("请配置 job.rag.embedding.model-name");
        }

        return OpenAiEmbeddingModel.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(embedding.getApiKey())
                .modelName(embedding.getModelName())
                .timeout(Duration.ofSeconds(embedding.getTimeoutSeconds()))
                .maxRetries(embedding.getMaxRetries())
                .logRequests(embedding.getLogRequests())
                .logResponses(embedding.getLogResponses())
                .build();
    }

    private String resolveJdbcUrl(RagProperties.Pgvector pgvector, RagProperties.DataSource datasourceProperties) {
        if (StringUtils.hasText(datasourceProperties.getJdbcUrl())) {
            return datasourceProperties.getJdbcUrl();
        }
        if (!StringUtils.hasText(pgvector.getHost()) || !StringUtils.hasText(pgvector.getDatabase())) {
            throw new IllegalStateException("请配置 job.rag.pgvector.datasource.jdbc-url 或 host/database");
        }
        return "jdbc:postgresql://" + pgvector.getHost() + ":" + pgvector.getPort() + "/" + pgvector.getDatabase();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
