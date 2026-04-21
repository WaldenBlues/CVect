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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 向量存储服务 - pgvector 操作封装
 *
 * 功能:
 * 1. 保存向量化后的 chunk 到 pgvector
 * 2. 基于向量相似度的候选人检索 (可配置向量索引)
 * 3. 批量操作优化
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);
    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final EmbeddingService embeddingService;
    private final VectorStoreConfig config;
    private final String tableName;
    private final AtomicBoolean vectorUnavailableLogged = new AtomicBoolean(false);
    private final AtomicBoolean indexCompatibilityChecked = new AtomicBoolean(false);
    private final Semaphore writeSemaphore;
    private final long writeAcquireTimeoutMs;
    private volatile boolean vectorAvailable;

    public VectorStoreService(
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager,
            EmbeddingService embeddingService,
            VectorStoreConfig config) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
        this.embeddingService = embeddingService;
        this.config = config;
        this.tableName = validateSqlIdentifier(config.getTableName(), "app.vector.table-name");
        this.writeSemaphore = new Semaphore(Math.max(1, config.getMaxConcurrentWrites()), true);
        this.writeAcquireTimeoutMs = Math.max(0L, config.getWriteAcquireTimeoutMs());
        this.vectorAvailable = initializeVectorSupport();
        ensureIndexCompatibility();
    }

    /**
     * 保存 chunk 并生成向量
     */
    @Transactional
    public boolean save(UUID candidateId, ChunkType chunkType, String content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (!config.isEnabled()) {
            log.info("Vector store disabled, skipping save for candidateId={}, chunkType={}", candidateId, chunkType);
            return false;
        }
        if (!vectorAvailable) {
            logVectorUnavailableOnce("save");
            return false;
        }
        boolean permitAcquired = false;
        try {
            permitAcquired = writeSemaphore.tryAcquire(writeAcquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting vector write permit, skip save: candidateId={}", candidateId);
            return false;
        }
        if (!permitAcquired) {
            log.warn("Vector write is throttled (permit timeout), skip save: candidateId={}, chunkType={}",
                    candidateId, chunkType);
            return false;
        }
        try {
            ensureIndexCompatibility();
            // 生成 embedding
            float[] embedding = embeddingService.embed(content);
            validateVectorInput(embedding, "embedding");

            // 保存到数据库
            ResumeChunkVector vector = new ResumeChunkVector(candidateId, chunkType, content, embedding);
            entityManager.persist(vector);

            log.debug("Saved vector chunk: candidateId={}, chunkType={}, contentLength={}",
                    candidateId, chunkType, content.length());
            return true;
        } finally {
            writeSemaphore.release();
        }
    }

    /**
     * 相似度搜索 - 基于 pgvector 的向量检索
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回前 K 个结果
     * @param chunkTypes     可选的 chunk 类型过滤
     * @return 按相似度排序的搜索结果
     */
    public List<SearchResult> search(float[] queryEmbedding, int topK, ChunkType... chunkTypes) {
        return search(queryEmbedding, topK, null, chunkTypes);
    }

    public List<SearchResult> search(
            float[] queryEmbedding,
            int topK,
            Collection<UUID> candidateIds,
            ChunkType... chunkTypes) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Vector store is disabled");
        }
        if (!vectorAvailable) {
            throw new IllegalStateException("pgvector extension is unavailable");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be > 0");
        }
        ensureIndexCompatibility();
        validateVectorInput(queryEmbedding, "queryEmbedding");
        StringBuilder sql = new StringBuilder();
        String queryVectorType = "vector(" + positiveDimension() + ")";
        sql.append("SELECT id, candidate_id, chunk_type, content, ");
        sql.append(normalizedEmbeddingExpression()).append(" <=> ?::").append(queryVectorType).append(" AS distance ");
        sql.append("FROM ").append(tableName).append(" ");
        sql.append("WHERE embedding IS NOT NULL ");

        List<Object> args = new ArrayList<>();
        args.add(vectorToString(queryEmbedding));

        if (candidateIds != null && !candidateIds.isEmpty()) {
            sql.append("AND candidate_id IN (");
            appendPlaceholders(sql, candidateIds.size());
            sql.append(") ");
            args.addAll(candidateIds);
        }

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
        args.add(topK);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sqlString,
                args.toArray());

        List<SearchResult> searchResults = new ArrayList<>();
        for (Map<String, Object> row : results) {
            float distance = ((Number) row.get("distance")).floatValue();
            ChunkType chunkType = parseChunkType(row.get("chunk_type"));
            if (chunkType == null) {
                log.warn("Skip invalid vector search row with unknown chunk_type: {}", row.get("chunk_type"));
                continue;
            }
            searchResults.add(new SearchResult(
                    (UUID) row.get("id"),
                    (UUID) row.get("candidate_id"),
                    chunkType,
                    (String) row.get("content"),
                    distance));
        }

        log.debug("Vector search returned {} results", searchResults.size());
        return searchResults;
    }

    public Map<UUID, CandidateScoreBreakdown> scoreCandidates(float[] queryEmbedding, Collection<UUID> candidateIds) {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Vector store is disabled");
        }
        if (!vectorAvailable) {
            throw new IllegalStateException("pgvector extension is unavailable");
        }
        ensureIndexCompatibility();
        validateVectorInput(queryEmbedding, "queryEmbedding");

        StringBuilder sql = new StringBuilder();
        String queryVectorType = "vector(" + positiveDimension() + ")";
        sql.append("SELECT candidate_id, chunk_type, ");
        sql.append("MAX(1 - (");
        sql.append(normalizedEmbeddingExpression()).append(" <=> ?::").append(queryVectorType);
        sql.append(")) AS score ");
        sql.append("FROM ").append(tableName).append(" ");
        sql.append("WHERE embedding IS NOT NULL ");

        List<Object> args = new ArrayList<>();
        args.add(vectorToString(queryEmbedding));
        if (candidateIds != null && !candidateIds.isEmpty()) {
            sql.append("AND candidate_id IN (");
            appendPlaceholders(sql, candidateIds.size());
            sql.append(") ");
            args.addAll(candidateIds);
        }
        sql.append("AND chunk_type IN ('EXPERIENCE', 'SKILL') ");
        sql.append("GROUP BY candidate_id, chunk_type");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        Map<UUID, MutableCandidateScoreBreakdown> rawScores = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object rawCandidateId = row.get("candidate_id");
            if (!(rawCandidateId instanceof UUID candidateId)) {
                log.warn("Skip invalid vector score row with invalid candidate_id: {}", rawCandidateId);
                continue;
            }
            ChunkType chunkType = parseChunkType(row.get("chunk_type"));
            if (chunkType == null) {
                continue;
            }
            Object rawScore = row.get("score");
            if (!(rawScore instanceof Number scoreValue)) {
                log.warn("Skip invalid vector score row with non-numeric score: {}", rawScore);
                continue;
            }
            float score = scoreValue.floatValue();
            if (!Float.isFinite(score)) {
                log.warn("Skip invalid vector score row with non-finite score: {}", rawScore);
                continue;
            }
            MutableCandidateScoreBreakdown current = rawScores.computeIfAbsent(
                    candidateId,
                    ignored -> new MutableCandidateScoreBreakdown());
            if (chunkType == ChunkType.EXPERIENCE) {
                current.experienceScore = Math.max(current.experienceScore, score);
            } else if (chunkType == ChunkType.SKILL) {
                current.skillScore = Math.max(current.skillScore, score);
            }
        }

        Map<UUID, CandidateScoreBreakdown> scores = new LinkedHashMap<>();
        for (Map.Entry<UUID, MutableCandidateScoreBreakdown> entry : rawScores.entrySet()) {
            scores.put(entry.getKey(), new CandidateScoreBreakdown(
                    entry.getValue().experienceScore,
                    entry.getValue().skillScore));
        }
        return scores;
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
        validateVectorInput(vector, "vector");
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
        String sql = "DELETE FROM " + tableName + " WHERE candidate_id = ?";
        int deleted = jdbcTemplate.update(sql, candidateId);
        log.info("Deleted {} vector chunks for candidate: {}", deleted, candidateId);
    }

    /**
     * 按 JD 删除所有相关候选人的向量
     */
    @Transactional
    public void deleteByJobDescription(UUID jobDescriptionId) {
        // Use subquery form for cross-database compatibility (PostgreSQL + H2 tests).
        String sql = "DELETE FROM " + tableName
                + " WHERE candidate_id IN (SELECT id FROM candidates WHERE jd_id = ?)";
        int deleted = jdbcTemplate.update(sql, jobDescriptionId);
        log.info("Deleted {} vector chunks for jd: {}", deleted, jobDescriptionId);
    }

    /**
     * 创建配置的向量索引 (首次运行或数据大量变化后调用)
     */
    @Transactional
    public void createVectorIndex() {
        if (!config.isEnabled()) {
            return;
        }
        if (!vectorAvailable) {
            logVectorUnavailableOnce("createVectorIndex");
            return;
        }
        String indexName = "idx_" + tableName + "_embedding";
        jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
        String indexType = resolveIndexType(config.getIndexType());
        String opClass = resolveVectorOpClass(config.getMetric());
        String sql;
        if ("hnsw".equals(indexType)) {
            sql = String.format(
                    "CREATE INDEX %s " +
                            "ON %s USING hnsw ((%s) %s) " +
                            "WITH (m = %d, ef_construction = %d)",
                    indexName,
                    tableName,
                    normalizedEmbeddingExpression(),
                    opClass,
                    config.getM(),
                    config.getEfConstruction());
        } else {
            sql = String.format(
                    "CREATE INDEX %s " +
                            "ON %s USING ivfflat ((%s) %s)",
                    indexName,
                    tableName,
                    normalizedEmbeddingExpression(),
                    opClass);
        }
        jdbcTemplate.execute(sql);
        log.info("Created {} index on {}.embedding with dimension={}", indexType.toUpperCase(Locale.ROOT), tableName, positiveDimension());
    }

    private boolean initializeVectorSupport() {
        if (!config.isEnabled()) {
            return false;
        }
        try {
            // pgvector 镜像支持直接创建扩展；若权限不足会在 catch 中降级。
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (Exception e) {
            log.warn("Unable to auto-create pgvector extension: {}", e.getMessage());
        }
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                    Boolean.class);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed checking pgvector extension availability: {}", e.getMessage());
        }
        log.warn("pgvector extension is unavailable; vector save/search/index will be skipped.");
        return false;
    }

    private void logVectorUnavailableOnce(String operation) {
        if (vectorUnavailableLogged.compareAndSet(false, true)) {
            log.warn("Skip vector operation '{}' because pgvector extension is unavailable.", operation);
        }
    }

    public boolean isOperational() {
        return config.isEnabled() && vectorAvailable;
    }

    public String getResolvedIndexType() {
        return resolveIndexType(config.getIndexType());
    }

    public String getAvailabilityMessage() {
        if (!config.isEnabled()) {
            return "Vector store disabled by configuration";
        }
        if (vectorAvailable) {
            return null;
        }
        return "pgvector extension is unavailable";
    }

    private void ensureIndexCompatibility() {
        if (!config.isEnabled() || !vectorAvailable) {
            return;
        }
        if (!indexCompatibilityChecked.compareAndSet(false, true)) {
            return;
        }

        String indexName = "idx_" + tableName + "_embedding";
        int expectedDimension = positiveDimension();
        String expectedIndexType = getResolvedIndexType();
        String expectedVectorType = "vector(" + expectedDimension + ")";
        String expectedOpClass = resolveVectorOpClass(config.getMetric());
        try {
            String indexDef = jdbcTemplate.query(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND tablename = ? AND indexname = ?",
                    rs -> rs.next() ? rs.getString("indexdef") : null,
                    tableName,
                    indexName);
            if (indexDef == null) {
                return;
            }
            String normalizedIndexDef = indexDef.toLowerCase(Locale.ROOT);
            if (normalizedIndexDef.contains("using " + expectedIndexType)
                    && normalizedIndexDef.contains(expectedOpClass)
                    && normalizedIndexDef.contains(expectedVectorType)) {
                return;
            }
            log.warn("Detected incompatible vector index '{}': {}. Rebuilding with type={}, opclass={}, dimension={}.",
                    indexName, indexDef, expectedIndexType, expectedOpClass, expectedDimension);
            jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
            createVectorIndex();
        } catch (Exception e) {
            log.warn("Failed to verify/rebuild vector index compatibility: {}", e.getMessage());
        }
    }

    private String normalizedEmbeddingExpression() {
        // JPA float[] 在 PostgreSQL text 列里通常是 {1,2,3}，需转成 pgvector 输入格式 [1,2,3]
        return "REPLACE(REPLACE(embedding, '{', '['), '}', ']')::vector(" + positiveDimension() + ")";
    }

    private static String resolveVectorOpClass(String metric) {
        if (metric == null) {
            return "vector_cosine_ops";
        }
        return switch (metric.toLowerCase(Locale.ROOT)) {
            case "l2" -> "vector_l2_ops";
            case "ip", "inner_product" -> "vector_ip_ops";
            case "cosine" -> "vector_cosine_ops";
            default -> throw new IllegalArgumentException("Unsupported vector metric: " + metric);
        };
    }

    private static String resolveIndexType(String indexType) {
        if (indexType == null) {
            return "hnsw";
        }
        return switch (indexType.toLowerCase(Locale.ROOT)) {
            case "hnsw" -> "hnsw";
            case "ivfflat" -> "ivfflat";
            default -> throw new IllegalArgumentException("Unsupported vector index type: " + indexType);
        };
    }

    private static ChunkType parseChunkType(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ChunkType.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int positiveDimension() {
        if (config.getDimension() <= 0) {
            throw new IllegalArgumentException("app.vector.dimension must be > 0");
        }
        return config.getDimension();
    }

    private void validateVectorInput(float[] vector, String fieldName) {
        if (vector == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        int expected = positiveDimension();
        if (vector.length != expected) {
            throw new IllegalArgumentException(
                    fieldName + " dimension mismatch: expected=" + expected + ", actual=" + vector.length);
        }
    }

    private static String validateSqlIdentifier(String value, String configKey) {
        if (value == null || value.isBlank() || !SAFE_SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier in " + configKey + ": " + value);
        }
        return value;
    }

    private static void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
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
            if (!Float.isFinite(distance)) {
                return 0.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, 1.0f - distance));
        }
    }

    public record CandidateScoreBreakdown(
            float experienceScore,
            float skillScore) {
    }

    private static final class MutableCandidateScoreBreakdown {
        private float experienceScore;
        private float skillScore;
    }
}
