package com.walden.cvect.service;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.fact.extract.FactExtractorDispatcher;
import com.walden.cvect.repository.FactRepository;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ResumeFactService {

    private final FactExtractorDispatcher dispatcher;
    private final FactRepository repository; // 假设的数据库操作类

    public ResumeFactService(FactExtractorDispatcher dispatcher, FactRepository repository) {
        this.dispatcher = dispatcher;
        this.repository = repository;
    }

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
                // EXPERIENCE 和 SKILL 暂不入库，后续用于 Embedding + 向量化
                case EXPERIENCE -> {
                    /* skip */ }
                case SKILL -> {
                    /* skip */ }
                default -> {
                }
            }
        }
    }

    private void handleContact(UUID candidateId, String data) {
        if (data.contains("@")) {
            repository.saveContact(candidateId, "EMAIL", data);
        } else {
            repository.saveContact(candidateId, "PHONE", data);
        }
    }

    private void handleEducation(UUID candidateId, String data) {
        String[] parts = data.split("\\|");
        String school = parts[0].trim();
        String major = parts.length > 1 ? parts[1].trim() : "";
        String degree = parts.length > 2 ? parts[2].trim() : "";
        repository.saveEducation(candidateId, school, major, degree);
    }

}