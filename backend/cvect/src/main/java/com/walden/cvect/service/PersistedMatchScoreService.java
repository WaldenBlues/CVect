package com.walden.cvect.service;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Service
public class PersistedMatchScoreService {

    private static final Logger log = LoggerFactory.getLogger(PersistedMatchScoreService.class);
    private static final float DEFAULT_EXPERIENCE_WEIGHT = 0.5f;
    private static final float DEFAULT_SKILL_WEIGHT = 0.5f;

    private final CandidateMatchScoreJpaRepository candidateMatchScoreRepository;
    private final JobDescriptionJpaRepository jobDescriptionRepository;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final ExecutorService refreshExecutor;
    private final Set<UUID> queuedJobDescriptionIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rerunJobDescriptionIds = ConcurrentHashMap.newKeySet();

    public PersistedMatchScoreService(
            CandidateMatchScoreJpaRepository candidateMatchScoreRepository,
            JobDescriptionJpaRepository jobDescriptionRepository,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            PlatformTransactionManager transactionManager,
            @Value("${app.match-scores.enabled:true}") boolean enabled) {
        this.candidateMatchScoreRepository = candidateMatchScoreRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
        this.refreshExecutor = Executors.newSingleThreadExecutor(new RefreshThreadFactory());
    }

    public void markJobDescriptionDirty(UUID jobDescriptionId) {
        if (!enabled || jobDescriptionId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            JobDescription jd = jobDescriptionRepository.findById(jobDescriptionId).orElse(null);
            if (jd != null) {
                jd.setEmbedding(null);
                jd.setEmbeddingUpdatedAt(null);
                jobDescriptionRepository.save(jd);
            }
            candidateMatchScoreRepository.deleteByJobDescriptionId(jobDescriptionId);
        });
    }

    public void scheduleRefreshForJobDescription(UUID jobDescriptionId) {
        if (!enabled || jobDescriptionId == null) {
            return;
        }
        if (!queuedJobDescriptionIds.add(jobDescriptionId)) {
            rerunJobDescriptionIds.add(jobDescriptionId);
            return;
        }
        refreshExecutor.submit(() -> {
            try {
                refreshForJobDescription(jobDescriptionId);
            } catch (Exception ex) {
                log.warn("Failed refreshing persisted match scores for jdId={}", jobDescriptionId, ex);
            } finally {
                queuedJobDescriptionIds.remove(jobDescriptionId);
                if (rerunJobDescriptionIds.remove(jobDescriptionId)) {
                    scheduleRefreshForJobDescription(jobDescriptionId);
                }
            }
        });
    }

    public void refreshForCandidate(UUID candidateId) {
        if (!enabled || candidateId == null) {
            return;
        }
        List<JobDescription> jobDescriptions = jobDescriptionRepository.findAll();
        if (jobDescriptions.isEmpty()) {
            transactionTemplate.executeWithoutResult(status ->
                    candidateMatchScoreRepository.deleteByCandidateId(candidateId));
            return;
        }
        LocalDateTime scoredAt = LocalDateTime.now();
        List<CandidateMatchScore> nextScores = new ArrayList<>();
        for (JobDescription jd : jobDescriptions) {
            float[] embedding = ensureJobDescriptionEmbedding(jd);
            if (embedding == null) {
                continue;
            }
            Map<UUID, VectorStoreService.CandidateScoreBreakdown> scores = vectorStoreService.scoreCandidates(
                    embedding,
                    List.of(candidateId));
            VectorStoreService.CandidateScoreBreakdown breakdown = scores.get(candidateId);
            if (breakdown == null) {
                continue;
            }
            nextScores.add(toEntity(candidateId, jd.getId(), breakdown, scoredAt));
        }
        transactionTemplate.executeWithoutResult(status -> {
            candidateMatchScoreRepository.deleteByCandidateId(candidateId);
            if (!nextScores.isEmpty()) {
                candidateMatchScoreRepository.saveAll(nextScores);
            }
        });
    }

    public void deleteByJobDescriptionId(UUID jobDescriptionId) {
        if (!enabled || jobDescriptionId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                candidateMatchScoreRepository.deleteByJobDescriptionId(jobDescriptionId));
    }

    public void deleteByCandidateIds(Collection<UUID> candidateIds) {
        if (!enabled || candidateIds == null || candidateIds.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                candidateMatchScoreRepository.deleteByCandidateIds(candidateIds));
    }

    private void refreshForJobDescription(UUID jobDescriptionId) {
        JobDescription jd = jobDescriptionRepository.findById(jobDescriptionId).orElse(null);
        if (jd == null) {
            transactionTemplate.executeWithoutResult(status ->
                    candidateMatchScoreRepository.deleteByJobDescriptionId(jobDescriptionId));
            return;
        }
        float[] embedding = ensureJobDescriptionEmbedding(jd);
        if (embedding == null) {
            transactionTemplate.executeWithoutResult(status ->
                    candidateMatchScoreRepository.deleteByJobDescriptionId(jobDescriptionId));
            return;
        }
        Map<UUID, VectorStoreService.CandidateScoreBreakdown> rawScores = vectorStoreService.scoreCandidates(embedding, null);
        LocalDateTime scoredAt = LocalDateTime.now();
        List<CandidateMatchScore> nextScores = rawScores.entrySet().stream()
                .map(entry -> toEntity(entry.getKey(), jobDescriptionId, entry.getValue(), scoredAt))
                .toList();
        transactionTemplate.executeWithoutResult(status -> {
            candidateMatchScoreRepository.deleteByJobDescriptionId(jobDescriptionId);
            if (!nextScores.isEmpty()) {
                candidateMatchScoreRepository.saveAll(nextScores);
            }
        });
    }

    private float[] ensureJobDescriptionEmbedding(JobDescription jd) {
        if (jd == null) {
            return null;
        }
        String normalizedContent = SearchCacheKeys.normalizeText(jd.getContent());
        if (normalizedContent.isBlank()) {
            if (jd.getEmbedding() != null || jd.getEmbeddingUpdatedAt() != null) {
                transactionTemplate.executeWithoutResult(status -> {
                    JobDescription current = jobDescriptionRepository.findById(jd.getId()).orElse(null);
                    if (current == null) {
                        return;
                    }
                    current.setEmbedding(null);
                    current.setEmbeddingUpdatedAt(null);
                    jobDescriptionRepository.save(current);
                });
            }
            return null;
        }
        float[] currentEmbedding = jd.getEmbedding();
        if (currentEmbedding != null && currentEmbedding.length > 0) {
            return currentEmbedding;
        }
        float[] embedded = embeddingService.embed(normalizedContent);
        transactionTemplate.executeWithoutResult(status -> {
            JobDescription current = jobDescriptionRepository.findById(jd.getId()).orElse(null);
            if (current == null) {
                return;
            }
            current.setEmbedding(embedded);
            current.setEmbeddingUpdatedAt(LocalDateTime.now());
            jobDescriptionRepository.save(current);
        });
        return embedded;
    }

    private CandidateMatchScore toEntity(
            UUID candidateId,
            UUID jobDescriptionId,
            VectorStoreService.CandidateScoreBreakdown breakdown,
            LocalDateTime scoredAt) {
        float experienceScore = clampScore(breakdown.experienceScore());
        float skillScore = clampScore(breakdown.skillScore());
        float weighted = experienceScore * DEFAULT_EXPERIENCE_WEIGHT + skillScore * DEFAULT_SKILL_WEIGHT;
        float overallScore = weighted > 0.0f ? weighted : Math.max(experienceScore, skillScore);
        return new CandidateMatchScore(
                candidateId,
                jobDescriptionId,
                overallScore,
                experienceScore,
                skillScore,
                scoredAt);
    }

    private static float clampScore(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    @PreDestroy
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    private static final class RefreshThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "cvect-match-score-refresh");
            thread.setDaemon(true);
            return thread;
        }
    }
}
