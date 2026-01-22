package com.walden.cvect.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class FactRepository {
    public void saveContact(UUID candidateId, String type, String value) {
        System.out.printf("Saving Contact: [%s] %s for %s%n", type, value, candidateId);
        // TODO: 接入数据库，如 MyBatis 或 JPA
    }

    public void saveLink(UUID candidateId, String url) {
        System.out.printf("Saving Link: %s for %s%n", url, candidateId);
    }

    public void saveHonor(UUID candidateId, String content) {
        System.out.printf("Saving Honor: %s for %s%n", content, candidateId);
    }
}