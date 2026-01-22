package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class JpaSmokeTest {

    @Bean
    CommandLineRunner testJpa(ContactJpaRepository repo) {
        return args -> {
            Contact c = new Contact(
                    UUID.randomUUID(),
                    "EMAIL",
                    "test@example.com");
            repo.save(c);
            System.out.println("✅ JPA save OK");
        };
    }
}
