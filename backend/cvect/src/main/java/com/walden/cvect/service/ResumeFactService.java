package com.walden.cvect.service;

import com.walden.cvect.infra.vector.VectorStoreService;
import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.fact.Regex;
import com.walden.cvect.model.fact.extract.FactExtractorDispatcher;
import com.walden.cvect.repository.FactRepository;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeFactService {

    private static final Logger log = LoggerFactory.getLogger(ResumeFactService.class);

    private final FactExtractorDispatcher dispatcher;
    private final FactRepository repository;
    private final VectorStoreService vectorStore;

    public ResumeFactService(
            FactExtractorDispatcher dispatcher,
            FactRepository repository,
            VectorStoreService vectorStore) {
        this.dispatcher = dispatcher;
        this.repository = repository;
        this.vectorStore = vectorStore;
    }

    /**
     * 提取chunk中的事实并持久化到数据库
     */
    public void processAndSave(UUID candidateId, ResumeChunk chunk) {

        List<String> extractedResults = dispatcher.extractAll(chunk);
        if (extractedResults.isEmpty()) {
            return;
        }

        for (String data : extractedResults) {
            if (data == null || data.isBlank()) {
                continue;
            }

            switch (chunk.getType()) {
                case CONTACT -> handleContact(candidateId, data);
                case LINK -> repository.saveLink(candidateId, data);
                case HONOR -> repository.saveHonor(candidateId, data);
                case EDUCATION -> handleEducation(candidateId, data);
                // EXPERIENCE 和 SKILL 向量化后存入 pgvector
                case EXPERIENCE, SKILL -> vectorStore.save(candidateId, chunk.getType(), chunk.getContent());
                default -> { }
            }
        }
    }

    private void handleContact(UUID candidateId, String data) {
        if (Regex.EMAIL_STRICT.matcher(data).find()) {
            repository.saveContact(candidateId, "EMAIL", data);
        } else if (Regex.PHONE_STRICT.matcher(data).find()) {
            repository.saveContact(candidateId, "PHONE", data);
        } else {
            log.debug("Unrecognized contact format: {}", data);
        }
    }

    private void handleEducation(UUID candidateId, String data) {
        String[] parts = data.split("\\|");
        if (parts.length < 1 || parts[0].trim().isEmpty()) {
            log.warn("Invalid education data format: {}", data);
            return;
        }
        String school = parts[0].trim();
        String major = parts.length > 1 ? parts[1].trim() : "";
        String degree = parts.length > 2 ? parts[2].trim() : "";
        repository.saveEducation(candidateId, school, major, degree);
    }

}