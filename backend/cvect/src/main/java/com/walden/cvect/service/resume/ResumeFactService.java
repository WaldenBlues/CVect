package com.walden.cvect.service.resume;

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

    public ResumeFactService(
            FactExtractorDispatcher dispatcher,
            FactRepository repository) {
        this.dispatcher = dispatcher;
        this.repository = repository;
    }

    /**
     * 提取chunk中的事实并持久化到数据库
     */
    public void processAndSave(UUID candidateId, ResumeChunk chunk) {

        List<String> extractedFacts = dispatcher.extractAll(chunk);
        if (extractedFacts.isEmpty()) {
            return;
        }

        for (String data : extractedFacts) {
            if (data == null || data.isBlank()) {
                continue;
            }

            handleExtractedData(candidateId, chunk, data);
        }
    }

    /**
     * 按 chunk 类型分发持久化逻辑
     */
    private void handleExtractedData(UUID candidateId, ResumeChunk chunk, String data) {
        switch (chunk.getType()) {
            case CONTACT -> handleContact(candidateId, data);
            case LINK -> repository.saveLink(candidateId, data);
            case HONOR -> repository.saveHonor(candidateId, data);
            case EDUCATION -> handleEducation(candidateId, data);
            case EXPERIENCE, SKILL -> { }
            default -> { }
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
