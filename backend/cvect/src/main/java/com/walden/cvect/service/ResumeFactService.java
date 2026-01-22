package com.walden.cvect.service;

import com.walden.cvect.model.ResumeChunk;
import com.walden.cvect.model.fact.extract.FactExtractorDispatcher;
import com.walden.cvect.repository.FactRepository;

import java.util.List;
import java.util.UUID;

public class ResumeFactService {

    private final FactExtractorDispatcher dispatcher;
    private final FactRepository repository; // 假设的数据库操作类

    public ResumeFactService(FactExtractorDispatcher dispatcher, FactRepository repository) {
        this.dispatcher = dispatcher;
        this.repository = repository;
    }

    public void processAndSave(UUID candidateId, ResumeChunk chunk) {
        // 1. 获取所有提取到的结果（可能是一个 Chunk 提取出多个 Fact）
        List<String> extractedResults = dispatcher.extractAll(chunk);

        if (extractedResults.isEmpty()) {
            return;
        }

        // 2. 遍历结果进行入库
        for (String data : extractedResults) {
            switch (chunk.getType()) {
                case CONTACT -> handleContactSave(candidateId, data);
                case LINK -> repository.saveLink(candidateId, data);
                case HONOR -> repository.saveHonor(candidateId, data);
                // 建议增加：EDUCATION, EXPERIENCE 等
                default -> {
                }
            }
        }
    }

    private void handleContactSave(UUID candidateId, String data) {
        // ContactExtractor 返回的是用 \n 分隔的多个结果
        String[] lines = data.split("\n");
        for (String line : lines) {
            // 简单的逻辑判断：包含 @ 为邮箱，否则视为电话
            String type = line.contains("@") ? "EMAIL" : "PHONE";
            repository.saveContact(candidateId, type, line);
        }
    }
}