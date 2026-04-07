package com.walden.cvect.service.matching;

import com.walden.cvect.infra.embedding.EmbeddingService;
import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.entity.CandidateMatchScore;
import com.walden.cvect.model.entity.JobDescription;
import com.walden.cvect.repository.CandidateMatchScoreJpaRepository;
import com.walden.cvect.repository.JobDescriptionJpaRepository;
import com.walden.cvect.service.matching.PersistedMatchScoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersistedMatchScoreService unit tests")
class PersistedMatchScoreServiceTest {

    @Mock
    private CandidateMatchScoreJpaRepository candidateMatchScoreRepository;
    @Mock
    private JobDescriptionJpaRepository jobDescriptionRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStoreService vectorStoreService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("markJobDescriptionDirty should clear persisted JD embedding and scores")
    void markJobDescriptionDirtyShouldClearEmbeddingAndScores() throws Exception {
        stubNoOpTransactions();
        UUID jdId = UUID.randomUUID();
        JobDescription jd = jobDescription(jdId, "Backend Engineer", "Java Spring");
        jd.setEmbedding(new float[] {0.2f, 0.4f});
        jd.setEmbeddingUpdatedAt(LocalDateTime.now());
        when(jobDescriptionRepository.findById(jdId)).thenReturn(Optional.of(jd));

        PersistedMatchScoreService service = new PersistedMatchScoreService(
                candidateMatchScoreRepository,
                jobDescriptionRepository,
                embeddingService,
                vectorStoreService,
                transactionManager,
                true);

        service.markJobDescriptionDirty(jdId);

        assertNull(jd.getEmbedding());
        assertNull(jd.getEmbeddingUpdatedAt());
        verify(jobDescriptionRepository).save(jd);
        verify(candidateMatchScoreRepository).deleteByJobDescriptionId(jdId);
    }

    @Test
    @DisplayName("refreshForCandidate should persist baseline scores for every JD")
    void refreshForCandidateShouldPersistScoresForAllJobDescriptions() throws Exception {
        stubNoOpTransactions();
        UUID candidateId = UUID.randomUUID();
        UUID jdId1 = UUID.randomUUID();
        UUID jdId2 = UUID.randomUUID();
        JobDescription jd1 = jobDescription(jdId1, "Java Engineer", "Spring Boot");
        JobDescription jd2 = jobDescription(jdId2, "Frontend Engineer", "Vue TypeScript");
        float[] jdEmbedding1 = new float[] {0.1f, 0.2f};
        float[] jdEmbedding2 = new float[] {0.3f, 0.4f};
        when(jobDescriptionRepository.findAll()).thenReturn(List.of(jd1, jd2));
        when(jobDescriptionRepository.findById(jdId1)).thenReturn(Optional.of(jd1));
        when(jobDescriptionRepository.findById(jdId2)).thenReturn(Optional.of(jd2));
        when(embeddingService.embed("Spring Boot")).thenReturn(jdEmbedding1);
        when(embeddingService.embed("Vue TypeScript")).thenReturn(jdEmbedding2);
        when(vectorStoreService.scoreCandidates(jdEmbedding1, List.of(candidateId))).thenReturn(Map.of(
                candidateId,
                new VectorStoreService.CandidateScoreBreakdown(0.8f, 0.4f)));
        when(vectorStoreService.scoreCandidates(jdEmbedding2, List.of(candidateId))).thenReturn(Map.of(
                candidateId,
                new VectorStoreService.CandidateScoreBreakdown(0.2f, 0.9f)));

        PersistedMatchScoreService service = new PersistedMatchScoreService(
                candidateMatchScoreRepository,
                jobDescriptionRepository,
                embeddingService,
                vectorStoreService,
                transactionManager,
                true);

        service.refreshForCandidate(candidateId);

        verify(candidateMatchScoreRepository).deleteByCandidateId(candidateId);
        verify(jobDescriptionRepository, times(2)).save(any(JobDescription.class));

        ArgumentCaptor<Iterable<CandidateMatchScore>> savedScores = ArgumentCaptor.forClass(Iterable.class);
        verify(candidateMatchScoreRepository).saveAll(savedScores.capture());
        List<CandidateMatchScore> persisted = toList(savedScores.getValue());
        assertEquals(2, persisted.size());

        CandidateMatchScore javaScore = persisted.stream()
                .filter(score -> jdId1.equals(score.getJobDescriptionId()))
                .findFirst()
                .orElseThrow();
        CandidateMatchScore frontendScore = persisted.stream()
                .filter(score -> jdId2.equals(score.getJobDescriptionId()))
                .findFirst()
                .orElseThrow();

        assertEquals(candidateId, javaScore.getCandidateId());
        assertEquals(0.6f, javaScore.getOverallScore(), 0.0001f);
        assertEquals(0.8f, javaScore.getExperienceScore(), 0.0001f);
        assertEquals(0.4f, javaScore.getSkillScore(), 0.0001f);
        assertNotNull(javaScore.getScoredAt());

        assertEquals(candidateId, frontendScore.getCandidateId());
        assertEquals(0.55f, frontendScore.getOverallScore(), 0.0001f);
        assertEquals(0.2f, frontendScore.getExperienceScore(), 0.0001f);
        assertEquals(0.9f, frontendScore.getSkillScore(), 0.0001f);
        assertNotNull(frontendScore.getScoredAt());
    }

    private void stubNoOpTransactions() {
        TransactionStatus txStatus = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
    }

    private static JobDescription jobDescription(UUID id, String title, String content) throws Exception {
        JobDescription jd = new JobDescription(title, content);
        Field idField = JobDescription.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(jd, id);
        return jd;
    }

    private static List<CandidateMatchScore> toList(Iterable<CandidateMatchScore> scores) {
        if (scores instanceof List<CandidateMatchScore> list) {
            return list;
        }
        return java.util.stream.StreamSupport.stream(scores.spliterator(), false).toList();
    }
}
