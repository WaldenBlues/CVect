package com.walden.cvect.infra.vector;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.model.ChunkType;
import com.walden.cvect.model.entity.vector.ResumeChunkVector;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 向量存储服务 - pgvector 操作封装
 *
 * 功能:
 * 1. 保存向量化后的 chunk 到 pgvector
 * 2. 基于向量相似度的候选人检索 (HNSW 索引)
 * 3. 批量操作优化
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final EmbeddingService embeddingService;
    private final VectorStoreConfig config;

    public VectorStoreService(
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager,
            EmbeddingService embeddingService,
            VectorStoreConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.embeddingService = embeddingService;
        this.config = config;
    }

    /**
     * 保存 chunk 并生成向量
     */
    @Transactional
    public void save(UUID candidateId, ChunkType chunkType, String content) {
        // 生成 embedding
        float[] embedding = embeddingService.embed(content);

        // 保存到数据库
        ResumeChunkVector vector = new ResumeChunkVector(candidateId, chunkType, content, embedding);
        entityManager.persist(vector);

        log.debug("Saved vector chunk: candidateId={}, chunkType={}, contentLength={}",
                candidateId, chunkType, content.length());
    }

    /**
     * 相似度搜索 - 基于余弦相似度的 HNSW 检索
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回前 K 个结果
     * @param chunkTypes     可选的 chunk 类型过滤
     * @return 按相似度排序的搜索结果
     */
    public List<SearchResult> search(float[] queryEmbedding, int topK, ChunkType... chunkTypes) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, candidate_id, chunk_type, content, embedding ");
        sql.append("<=> ?::vector AS distance ");
        sql.append("FROM resume_chunks ");
        sql.append("WHERE 1=1 ");

        // 类型过滤 - 使用验证后的枚举值拼接，确保安全
        if (chunkTypes != null && chunkTypes.length > 0) {
            sql.append("AND chunk_type IN (");
            for (int i = 0; i < chunkTypes.length; i++) {
                if (i > 0)
                    sql.append(", ");
                // ChunkType 是枚举，name() 返回安全字符串
                // 此处使用白名单验证，确保只有有效的枚举值被拼接
                String typeName = chunkTypes[i].name();
                validateChunkType(typeName);
                sql.append("'").append(typeName).append("'");
            }
            sql.append(") ");
        }

        sql.append("ORDER BY distance ASC ");
        sql.append("LIMIT ?");

        String sqlString = sql.toString();
        String vectorString = vectorToString(queryEmbedding);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sqlString,
                vectorString,
                topK);

        List<SearchResult> searchResults = new ArrayList<>();
        for (Map<String, Object> row : results) {
            searchResults.add(new SearchResult(
                    (UUID) row.get("id"),
                    (UUID) row.get("candidate_id"),
                    ChunkType.valueOf((String) row.get("chunk_type")),
                    (String) row.get("content"),
                    ((Number) row.get("distance")).floatValue()));
        }

        log.debug("Vector search returned {} results", searchResults.size());
        return searchResults;
    }

    /**
     * 验证 ChunkType 是否为有效枚举值
     * 作为白名单检查，确保 SQL 拼接安全
     */
    private void validateChunkType(String typeName) {
        for (ChunkType type : ChunkType.values()) {
            if (type.name().equals(typeName)) {
                return;
            }
        }
        throw new IllegalArgumentException("Invalid ChunkType: " + typeName);
    }

    /**
     * 将 float[] 转换为 PostgreSQL vector 格式字符串
     * pgvector 格式: [1.0, 2.0, 3.0]
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 按候选人 ID 删除所有向量
     */
    @Transactional
    public void deleteByCandidate(UUID candidateId) {
        String sql = "DELETE FROM resume_chunks WHERE candidate_id = ?";
        int deleted = jdbcTemplate.update(sql, candidateId);
        log.info("Deleted {} vector chunks for candidate: {}", deleted, candidateId);
    }

    /**
     * 创建 HNSW 索引 (首次运行或数据大量变化后调用)
     */
    @Transactional
    public void createHnswIndex() {
        String sql = String.format(
                "CREATE INDEX IF NOT EXISTS idx_resume_chunks_embedding " +
                        "ON resume_chunks USING hnsw (embedding %s_ops) " +
                        "WITH (m = %d, ef_construction = %d)",
                config.getMetric(),
                config.getM(),
                config.getEfConstruction());
        jdbcTemplate.execute(sql);
        log.info("Created HNSW index on resume_chunks.embedding");
    }

    /**
     * 搜索结果封装
     */
    public record SearchResult(
            UUID id,
            UUID candidateId,
            ChunkType chunkType,
            String content,
            float distance) {
        // distance 越小表示越相似 (余弦距离)
        public float score() {
            return 1.0f - distance;
        }
    }
}
