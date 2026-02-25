package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Candidate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class JpaSmokeTest {

    @Bean
    CommandLineRunner testJpa(ContactJpaRepository repo, CandidateJpaRepository candidateRepository) {
        return args -> {
            Candidate candidate = candidateRepository.save(new Candidate(
                    "smoke.pdf",
                    UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                    "Smoke Candidate",
                    null,
                    "application/pdf",
                    1L,
                    1,
                    false));
            Contact c = new Contact(
                    candidate.getId(),
                    "EMAIL",
                    "test@example.com");
            repo.save(c);
            System.out.println("✅ JPA save OK");
        };
    }
}
