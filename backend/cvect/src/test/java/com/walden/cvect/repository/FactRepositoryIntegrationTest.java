package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.Education;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Tag("integration")
@Tag("repository")
class FactRepositoryIntegrationTest {

    @Autowired
    private FactRepository factRepository;

    @Autowired
    private ContactJpaRepository contactRepository;

    @Autowired
    private LinkJpaRepository linkRepository;

    @Autowired
    private HonorJpaRepository honorRepository;

    @Autowired
    private EducationJpaRepository educationRepository;

    private UUID testCandidateId;

    @BeforeEach
    void setUp() {
        testCandidateId = UUID.randomUUID();
    }

    @Test
    @DisplayName("保存 Contact 应成功")
    void should_save_contact_successfully() {
        // When
        assertDoesNotThrow(() -> {
            factRepository.saveContact(testCandidateId, "EMAIL", "test@example.com");
        });

        // Then
        List<Contact> contacts = contactRepository.findByCandidateId(testCandidateId);
        assertFalse(contacts.isEmpty(), "应保存 contact 记录");
        assertEquals(1, contacts.size());
    }

    @Test
    @DisplayName("保存 Link 应成功")
    void should_save_link_successfully() {
        // When
        assertDoesNotThrow(() -> {
            factRepository.saveLink(testCandidateId, "https://github.com/username");
        });

        // Then
        List<Link> links = linkRepository.findByCandidateId(testCandidateId);
        assertFalse(links.isEmpty());
        assertEquals(1, links.size());
    }

    @Test
    @DisplayName("保存 Honor 应成功")
    void should_save_honor_successfully() {
        // When
        assertDoesNotThrow(() -> {
            factRepository.saveHonor(testCandidateId, "全国大学生数学建模竞赛一等奖");
        });

        // Then
        List<Honor> honors = honorRepository.findByCandidateId(testCandidateId);
        assertFalse(honors.isEmpty());
        assertEquals(1, honors.size());
    }

    @Test
    @DisplayName("保存 Education 应成功")
    void should_save_education_successfully() {
        // When
        assertDoesNotThrow(() -> {
            factRepository.saveEducation(testCandidateId, "清华大学", "计算机科学与技术", "学士");
        });

        // Then
        List<Education> educations = educationRepository.findByCandidateId(testCandidateId);
        assertFalse(educations.isEmpty());
        assertEquals(1, educations.size());
    }

    @Test
    @DisplayName("保存多个 Contact 应分别存储")
    void should_save_multiple_contacts() {
        // When
        factRepository.saveContact(testCandidateId, "EMAIL", "test@example.com");
        factRepository.saveContact(testCandidateId, "PHONE", "13800138000");

        // Then
        List<Contact> contacts = contactRepository.findByCandidateId(testCandidateId);
        assertEquals(2, contacts.size());
    }

    @Test
    @DisplayName("不同 candidateId 的数据应隔离")
    void should_isolate_data_by_candidate_id() {
        // Given
        UUID candidateId1 = UUID.randomUUID();
        UUID candidateId2 = UUID.randomUUID();

        // When
        factRepository.saveContact(candidateId1, "EMAIL", "user1@example.com");
        factRepository.saveContact(candidateId2, "EMAIL", "user2@example.com");

        // Then
        List<Contact> contacts1 = contactRepository.findByCandidateId(candidateId1);
        List<Contact> contacts2 = contactRepository.findByCandidateId(candidateId2);

        assertEquals(1, contacts1.size());
        assertEquals(1, contacts2.size());
    }
}
