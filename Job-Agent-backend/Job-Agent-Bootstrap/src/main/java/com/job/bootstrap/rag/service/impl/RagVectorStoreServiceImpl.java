package com.job.bootstrap.rag.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.model.RagTextChunk;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.rag.RagDocumentTypeStatsVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.common.vo.rag.RagStatsVO;
import com.job.exception.BizException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 作者:hfj
 * 功能:pgvector 向量库访问实现
 * 日期:2026/6/14
 */
@Service
public class RagVectorStoreServiceImpl implements RagVectorStoreService {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int PGVECTOR_IVFFLAT_MAX_DIMENSION = 2000;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public RagVectorStoreServiceImpl(
            @Qualifier("ragJdbcTemplate") JdbcTemplate jdbcTemplate,
            RagProperties ragProperties,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 确保 pgvector 扩展、表和索引存在。
     */
    @Override
    public void ensureSchema() {
        String table = tableName();
        String metadataColumn = metadataColumn();
        Integer dimension = ragProperties.getPgvector().getDimension();

        /*
         * 1. vector 是 pgvector 扩展提供的数据类型。
         * 2. 如果数据库已经手动安装扩展，这条 SQL 不会重复创建。
         * 3. 如果当前数据库用户没有 CREATE EXTENSION 权限，需要 DBA 先执行一次。
         */
        ensureVectorExtensionReady();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    document_type VARCHAR(64) NOT NULL,
                    business_id BIGINT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    title VARCHAR(255),
                    content TEXT NOT NULL,
                    source VARCHAR(255),
                    %s JSONB NOT NULL DEFAULT '{}'::jsonb,
                    embedding VECTOR(%d) NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT %s UNIQUE (user_id, document_type, business_id, chunk_index)
                )
                """.formatted(table, metadataColumn, dimension, uniqueConstraintName(table)));

        /*
         * 1. 普通 BTree 索引用于按用户和文档类型清理、过滤。
         * 2. ivfflat 向量索引用于 cosine 相似度检索。
         * 3. demo 阶段 lists=100 足够，数据量变大后可按向量总量调优。
         */
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS " + indexName(table, "user_type") +
                        " ON " + table + " (user_id, document_type)"
        );
        /*
         * pgvector 的 ivfflat/hnsw 索引对 vector 类型有维度上限。
         * 当前项目配置的 embedding 维度是 2048，超过 ivfflat 的 2000 维限制。
         * 这种情况下跳过向量索引，检索 SQL 仍然可以用 <=> 做精确相似度扫描。
         */
        if (dimension != null && dimension <= PGVECTOR_IVFFLAT_MAX_DIMENSION) {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS " + indexName(table, "embedding") +
                            " ON " + table + " USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
            );
        }
    }

    /**
     * 删除某个用户指定类型的全部向量文档。
     *
     * @param userId 用户 ID，0 表示公共知识
     * @param documentTypes 文档类型集合
     */
    @Override
    public void deleteDocuments(Long userId, Collection<String> documentTypes) {
        if (userId == null || CollectionUtils.isEmpty(documentTypes)) {
            return;
        }

        String placeholders = String.join(",", documentTypes.stream().map(type -> "?").toList());
        String sql = "DELETE FROM " + tableName() + " WHERE user_id = ? AND document_type IN (" + placeholders + ")";

        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(documentTypes);
        jdbcTemplate.update(sql, args.toArray());
    }

    /**
     * 删除某个业务文档的所有分片。
     *
     * @param userId 用户 ID
     * @param documentType 文档类型
     * @param businessId 业务主键 ID
     */
    @Override
    public void deleteDocument(Long userId, String documentType, Long businessId) {
        if (userId == null || !StringUtils.hasText(documentType) || businessId == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM " + tableName() + " WHERE user_id = ? AND document_type = ? AND business_id = ?",
                userId,
                documentType,
                businessId
        );
    }

    /**
     * 写入文本分片。
     *
     * @param chunks 已经带有 embedding 的文本分片
     */
    @Override
    public void saveChunks(List<RagTextChunk> chunks) {
        if (CollectionUtils.isEmpty(chunks)) {
            return;
        }

        ensureSchema();

        String sql = """
                INSERT INTO %s (
                    user_id, document_type, business_id, chunk_index, title, content, source, %s, embedding, content_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?)
                ON CONFLICT (user_id, document_type, business_id, chunk_index)
                DO UPDATE SET
                    title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    source = EXCLUDED.source,
                    %s = EXCLUDED.%s,
                    embedding = EXCLUDED.embedding,
                    content_hash = EXCLUDED.content_hash,
                    update_time = CURRENT_TIMESTAMP
                """.formatted(tableName(), metadataColumn(), metadataColumn(), metadataColumn());

        jdbcTemplate.batchUpdate(sql, chunks, 50, this::fillChunkStatement);
    }

    /**
     * 相似度检索。
     *
     * @param userId 当前用户 ID
     * @param queryVector 问题向量
     * @param limit 召回条数
     * @param minScore 最低相似度
     * @return 召回结果
     */
    @Override
    public List<RagSearchResultVO> search(Long userId, float[] queryVector, int limit, double minScore) {
        ensureSchema();

        String sql = """
                WITH query_vector AS (SELECT ?::vector AS embedding)
                SELECT
                    t.id,
                    t.user_id,
                    t.document_type,
                    t.business_id,
                    t.chunk_index,
                    t.title,
                    t.content,
                    t.source,
                    t.%s,
                    1 - (t.embedding <=> q.embedding) AS score
                FROM %s t
                CROSS JOIN query_vector q
                WHERE t.user_id IN (0, ?)
                  AND 1 - (t.embedding <=> q.embedding) >= ?
                ORDER BY t.embedding <=> q.embedding
                LIMIT ?
                """.formatted(metadataColumn(), tableName());

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    RagSearchResultVO vo = new RagSearchResultVO();
                    vo.setId(rs.getLong("id"));
                    vo.setUserId(rs.getLong("user_id"));
                    vo.setDocumentType(rs.getString("document_type"));
                    vo.setBusinessId(rs.getLong("business_id"));
                    vo.setChunkIndex(rs.getInt("chunk_index"));
                    vo.setTitle(rs.getString("title"));
                    vo.setContent(rs.getString("content"));
                    vo.setSource(rs.getString("source"));
                    vo.setScore(rs.getDouble("score"));
                    vo.setVectorScore(vo.getScore());
                    vo.setRetrievalSource("VECTOR");

                    Map<String, Object> metadata = readMetadata(rs.getString(metadataColumn()));
                    vo.setMetadata(metadata);
                    vo.setDocumentId(getLong(metadata, "documentId"));
                    vo.setChunkId(getLong(metadata, "chunkId"));
                    vo.setPermissionScope(getString(metadata, "permissionScope"));
                    return vo;
                },
                vectorLiteral(queryVector),
                userId,
                minScore,
                limit
        );
    }

    /**
     * 查询向量库统计信息。
     *
     * @return RAG 知识库统计
     */
    @Override
    public RagStatsVO getStats() {
        RagStatsVO stats = new RagStatsVO();
        stats.setTableName(tableName());
        stats.setDimension(ragProperties.getPgvector().getDimension());
        stats.setMaxResults(ragProperties.getRetrieval().getMaxResults());
        stats.setMinScore(ragProperties.getRetrieval().getMinScore());
        stats.setChunkSize(ragProperties.getRetrieval().getChunk().getSize());
        stats.setChunkOverlap(ragProperties.getRetrieval().getChunk().getOverlap());

        /*
         * 1. 统计接口只负责展示当前 RAG 初始化状态。
         * 2. 如果 job_user 没有 CREATE EXTENSION 权限，不应该在这里抛系统异常。
         * 3. 缺少 pgvector 扩展时，直接把修复命令返回给后台页面展示。
         */
        if (!isVectorExtensionReady()) {
            stats.setSetupMessage(vectorExtensionSetupMessage());
            return stats;
        }

        stats.setExtensionReady(true);

        try {
            ensureSchema();
            stats.setTableReady(isKnowledgeTableReady());
            stats.setSchemaReady(Boolean.TRUE.equals(stats.getTableReady()));
        } catch (BizException | DataAccessException exception) {
            stats.setSetupMessage(exception.getMessage());
            return stats;
        }

        /*
         * 1. user_id=0 是公共知识。
         * 2. user_id>0 是普通用户私有知识。
         * 3. 这里用 FILTER 聚合一次性算出总量、公共量和私有量。
         */
        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*)::int AS total_chunks,
                    COUNT(*) FILTER (WHERE user_id = 0)::int AS public_chunks,
                    COUNT(*) FILTER (WHERE user_id <> 0)::int AS private_chunks
                FROM %s
                """.formatted(tableName()));

        stats.setTotalChunks(toInt(summary.get("total_chunks")));
        stats.setPublicChunks(toInt(summary.get("public_chunks")));
        stats.setPrivateChunks(toInt(summary.get("private_chunks")));

        List<RagDocumentTypeStatsVO> typeStats = jdbcTemplate.query("""
                SELECT
                    user_id,
                    document_type,
                    COUNT(DISTINCT business_id)::int AS document_count,
                    COUNT(*)::int AS chunk_count
                FROM %s
                GROUP BY user_id, document_type
                ORDER BY user_id, document_type
                """.formatted(tableName()), (rs, rowNum) -> {
            RagDocumentTypeStatsVO vo = new RagDocumentTypeStatsVO();
            vo.setUserId(rs.getLong("user_id"));
            vo.setDocumentType(rs.getString("document_type"));
            vo.setDocumentCount(rs.getInt("document_count"));
            vo.setChunkCount(rs.getInt("chunk_count"));
            return vo;
        });

        stats.setTypeStats(typeStats);
        return stats;
    }

    private void ensureVectorExtensionReady() {
        if (isVectorExtensionReady()) {
            return;
        }

        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (DataAccessException exception) {
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), vectorExtensionSetupMessage(), exception);
        }

        if (!isVectorExtensionReady()) {
            throw new BizException(vectorExtensionSetupMessage());
        }
    }

    private boolean isVectorExtensionReady() {
        Boolean ready = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class
        );
        return Boolean.TRUE.equals(ready);
    }

    private boolean isKnowledgeTableReady() {
        Boolean ready = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                tableName()
        );
        return Boolean.TRUE.equals(ready);
    }

    private String vectorExtensionSetupMessage() {
        return "pgvector 扩展没有在当前 RAG 数据库启用。请用数据库超级用户进入 job_rag 后执行: CREATE EXTENSION IF NOT EXISTS vector;";
    }

    private void fillChunkStatement(PreparedStatement ps, RagTextChunk chunk) throws SQLException {
        ps.setLong(1, chunk.getUserId());
        ps.setString(2, chunk.getDocumentType().name());
        ps.setLong(3, chunk.getBusinessId());
        ps.setInt(4, chunk.getChunkIndex());
        ps.setString(5, chunk.getTitle());
        ps.setString(6, chunk.getContent());
        ps.setString(7, chunk.getSource());
        ps.setString(8, writeMetadata(chunk.getMetadata()));
        ps.setString(9, vectorLiteral(chunk.getEmbedding()));
        ps.setString(10, chunk.getContentHash());
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalStateException("RAG 元数据序列化失败", e);
        }
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            return Map.of("raw", metadataJson);
        }
    }

    private Long getLong(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    private String getString(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String vectorLiteral(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空");
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("向量中存在非法数值");
            }
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String tableName() {
        return safeIdentifier(ragProperties.getPgvector().getTable(), "job_knowledge");
    }

    private String metadataColumn() {
        return safeIdentifier(ragProperties.getPgvector().getMetadataColumn(), "metadata");
    }

    private String safeIdentifier(String value, String defaultValue) {
        String identifier = StringUtils.hasText(value) ? value : defaultValue;
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("非法数据库标识符: " + identifier);
        }
        return identifier;
    }

    private String uniqueConstraintName(String table) {
        return safeIdentifier(table + "_uk_doc_chunk", "job_knowledge_uk_doc_chunk");
    }

    private String indexName(String table, String suffix) {
        return safeIdentifier("idx_" + table + "_" + suffix, "idx_job_knowledge_" + suffix);
    }
}
