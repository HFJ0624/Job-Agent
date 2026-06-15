package com.job.bootstrap.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 作者:hfj
 * 功能:RAG 知识库配置属性
 * 日期:2026/6/14
 */
@Data
@ConfigurationProperties(prefix = "job.rag")
public class RagProperties {

    /**
     * pgvector 向量库配置。
     * 这里单独配置，不复用 spring.datasource，避免 RAG 库和主业务 MySQL 库混在一起。
     */
    private Pgvector pgvector = new Pgvector();

    /**
     * Embedding 模型配置。
     * 用于把简历、JD、公司和沟通记录转换成向量。
     */
    private Embedding embedding = new Embedding();

    /**
     * 检索和切片配置。
     */
    private Retrieval retrieval = new Retrieval();

    @Data
    public static class Pgvector {

        /**
         * PostgreSQL 主机。
         * 保留该字段是为了让配置更直观，实际 JDBC 连接优先使用 datasource.jdbc-url。
         */
        private String host;

        /**
         * PostgreSQL 端口。
         */
        private Integer port = 5432;

        /**
         * RAG 数据库名称。
         */
        private String database;

        /**
         * 向量表名称。
         */
        private String table = "job_knowledge";

        /**
         * embedding 向量维度。
         * 必须和 Embedding 模型真实返回的维度一致，否则 pgvector 无法写入。
         */
        private Integer dimension = 2048;

        /**
         * 元数据 JSONB 列名。
         * application-local.yml 中已经配置 metadata-column，这里保持可配置，方便后续改表结构。
         */
        private String metadataColumn = "metadata";

        /**
         * pgvector 用户名。
         * 如果 datasource.username 为空，可用该字段兜底。
         */
        private String user;

        /**
         * JDBC 数据源配置。
         */
        private DataSource datasource = new DataSource();
    }

    @Data
    public static class DataSource {

        /**
         * PostgreSQL JDBC URL。
         */
        private String jdbcUrl;

        /**
         * 数据库用户名。
         */
        private String username;

        /**
         * 数据库密码。
         */
        private String password;

        /**
         * PostgreSQL JDBC 驱动类名。
         */
        private String driverClassName = "org.postgresql.Driver";
    }

    @Data
    public static class Embedding {

        /**
         * OpenAI 兼容 Embedding 接口地址。
         */
        private String baseUrl;

        /**
         * Embedding API Key。
         */
        private String apiKey;

        /**
         * Embedding 模型名称。
         */
        private String modelName;

        /**
         * Embedding 请求模式。
         * auto: 根据 modelName 自动判断，包含 vision/multimodal 时走方舟多模态请求体。
         * openai-text: 走普通文本向量化请求体，适合纯文本 embedding 模型。
         * ark-multimodal: 走火山方舟多模态向量化请求体，适合 doubao-embedding-vision-*。
         */
        private String requestMode = "auto";

        /**
         * 自定义 Embedding API 路径。
         * 为空时由 requestMode 自动选择，便于火山方舟接口路径调整时只改 yml、不改代码。
         */
        private String apiPath;

        /**
         * 普通文本向量化接口路径。
         */
        private String textApiPath = "/embeddings";

        /**
         * 多模态向量化接口路径。
         */
        private String multimodalApiPath = "/embeddings/multimodal";

        /**
         * 向量返回格式。
         * 为空时不发送该字段，避免多模态接口不支持该参数。
         */
        private String encodingFormat;

        /**
         * 请求超时时间，单位秒。
         */
        private Long timeoutSeconds = 60L;

        /**
         * 模型调用失败后的最大重试次数。
         */
        private Integer maxRetries = 2;

        /**
         * 是否打印 Embedding 请求日志。
         */
        private Boolean logRequests = false;

        /**
         * 是否打印 Embedding 响应日志。
         */
        private Boolean logResponses = false;
    }

    @Data
    public static class Retrieval {

        /**
         * 默认召回条数。
         */
        private Integer maxResults = 4;

        /**
         * 最低相似度得分。
         */
        private Double minScore = 0.5D;

        /**
         * 文本切片配置。
         */
        private Chunk chunk = new Chunk();
    }

    @Data
    public static class Chunk {

        /**
         * 每个文本分片的最大字符数。
         */
        private Integer size = 500;

        /**
         * 相邻文本分片的重叠字符数。
         * 保留重叠是为了避免一句话刚好被切断后语义丢失。
         */
        private Integer overlap = 80;
    }
}
