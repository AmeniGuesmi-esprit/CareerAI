package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.CvUpload;
import com.careerai.skillgap.model.SkillGapAnalysis;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.UUID;

/** Données de test communes aux tests des repositories. */
final class RepositoryTestSupport {

    /** Base H2 en mémoire en mode MySQL : les vraies requêtes SQL sont exécutées, sans XAMPP. */
    static final String H2_URL = "spring.datasource.url=jdbc:h2:mem:skillgap;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    static final String H2_DRIVER = "spring.datasource.driver-class-name=org.h2.Driver";
    static final String H2_USER = "spring.datasource.username=sa";
    static final String H2_PASSWORD = "spring.datasource.password=";
    static final String DDL = "spring.jpa.hibernate.ddl-auto=create-drop";

    private RepositoryTestSupport() {}

    static CvUpload cv(Long userId, String name) {
        return CvUpload.builder()
                .userId(userId)
                .originalFileName(name)
                .fileType("pdf")
                .storedFileName(UUID.randomUUID() + ".pdf")
                .sizeBytes(1024)
                .extractedText("Java, Spring Boot, SQL")
                .build();
    }

    static SkillGapAnalysis analysis(Long userId, CvUpload cv, String jobTitle) {
        return SkillGapAnalysis.builder()
                .userId(userId)
                .cv(cv)
                .jobTitle(jobTitle)
                .jobDescription("Développeur Java : Spring Boot, Docker, Kubernetes")
                .resultJson("{\"skills\":[]}")
                .matchScore(70)
                .model("openai/gpt-oss-120b")
                .build();
    }

    /**
     * Fixe une date de création précise. Les colonnes de date sont « updatable = false » et remplies par
     * {@code @PrePersist} : seule une mise à jour JPQL permet de rendre l'ordre des résultats déterministe.
     */
    static void setDate(EntityManager em, Class<?> entity, String field, Long id, LocalDateTime date) {
        em.flush();
        em.createQuery("update " + entity.getSimpleName() + " e set e." + field + " = :d where e.id = :id")
                .setParameter("d", date).setParameter("id", id).executeUpdate();
        em.clear();
    }
}
