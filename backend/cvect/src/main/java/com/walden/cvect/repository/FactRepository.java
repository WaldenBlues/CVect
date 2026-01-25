package com.walden.cvect.repository;

import com.walden.cvect.model.entity.Contact;
import com.walden.cvect.model.entity.Honor;
import com.walden.cvect.model.entity.Link;
import com.walden.cvect.model.entity.Education;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * 事实数据仓储（JPA facade）
 */
@Repository
public class FactRepository {

    private static final Logger log = LoggerFactory.getLogger(FactRepository.class);

    private final ContactJpaRepository contactRepository;
    private final LinkJpaRepository linkRepository;
    private final HonorJpaRepository honorRepository;
    private final EducationJpaRepository educationRepository;

    public FactRepository(
            ContactJpaRepository contactRepository,
            LinkJpaRepository linkRepository,
            HonorJpaRepository honorRepository,
            EducationJpaRepository educationRepository) {
        this.contactRepository = contactRepository;
        this.linkRepository = linkRepository;
        this.honorRepository = honorRepository;
        this.educationRepository = educationRepository;
    }

    public void saveContact(UUID candidateId, String type, String value) {
        contactRepository.save(new Contact(candidateId, type, value));
        log.debug("Saved Contact [{}:{}]", type, value);
    }

    public void saveLink(UUID candidateId, String url) {
        linkRepository.save(new Link(candidateId, url));
        log.debug("Saved Link [{}]", url);
    }

    public void saveHonor(UUID candidateId, String content) {
        honorRepository.save(new Honor(candidateId, content));
        log.debug("Saved Honor [{}]", content);
    }

    public void saveEducation(UUID candidateId, String school, String major, String degree) {
        educationRepository.save(new Education(candidateId, school, major, degree));
        log.debug("Saved Education [{} | {} | {}]", school, major, degree);
    }
}
