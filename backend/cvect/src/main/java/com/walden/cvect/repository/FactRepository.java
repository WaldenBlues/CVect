package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.model.entity.Education;
import com.walden.cvect.model.entity.Experience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class FactRepository {

    private static final Logger log = LoggerFactory.getLogger(FactRepository.class);

    private final ContactJpaRepository contactRepository;
    private final LinkJpaRepository linkRepository;
    private final HonorJpaRepository honorRepository;
    private final EducationJpaRepository educationRepository;
    private final ExperienceJpaRepository experienceRepository;

    public FactRepository(
            ContactJpaRepository contactRepository,
            LinkJpaRepository linkRepository,
            HonorJpaRepository honorRepository,
            EducationJpaRepository educationRepository,
            ExperienceJpaRepository experienceRepository) {
        this.contactRepository = contactRepository;
        this.linkRepository = linkRepository;
        this.honorRepository = honorRepository;
        this.educationRepository = educationRepository;
        this.experienceRepository = experienceRepository;
    }

    public void saveContact(UUID candidateId, String type, String value) {
        contactRepository.save(new Contact(candidateId, type, value));
        log.info("Saved Contact [{}:{}]", type, value);
    }

    public void saveLink(UUID candidateId, String url) {
        linkRepository.save(new Link(candidateId, url));
        log.info("Saved Link [{}]", url);
    }

    public void saveHonor(UUID candidateId, String content) {
        honorRepository.save(new Honor(candidateId, content));
        log.info("Saved Honor [{}]", content);
    }

    public void saveEducation(UUID candidateId, String school, String major, String degree) {
        educationRepository.save(new Education(candidateId, school, major, degree));
        log.info("Saved Education [{} | {} | {}]", school, major, degree);
    }

    public void saveExperience(UUID candidateId, String company, String position, String description) {
        experienceRepository.save(
                new Experience(candidateId, company, position, description));
        log.info("Saved Experience [{} | {}]", company, position);
    }
}
