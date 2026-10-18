package com.careerai.skillgap.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Entité 2 : analyse CV / offre d'emploi (offre saisie + résultat JSON du LLM). */
@Entity
@Table(name = "skill_gap_analyses", indexes = @Index(columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SkillGapAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cv_id")
    private CvUpload cv;

    @Column(length = 200)
    private String jobTitle;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String jobDescription;

    /** Résultat structuré (AnalysisResult sérialisé en JSON). */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    /** Copie de matchScore pour pouvoir trier / filtrer sans parser le JSON. */
    private int matchScore;

    /** Modèle LLM utilisé (traçabilité). */
    @Column(nullable = false)
    private String model;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
