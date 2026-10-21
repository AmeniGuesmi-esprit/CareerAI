package com.careerai.compatibility_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analyses", indexes = @Index(name = "idx_analysis_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String cvFileName;

    @Column(nullable = false, length = 150)
    private String jobTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column(nullable = false)
    private int score;

    /** Résultat de l'IA sérialisé en JSON (résumé, points forts, manques, suggestions). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    // Mesures de l'appel IA (colonnes nullables : les analyses plus anciennes n'en ont pas)
    @Column(length = 80)
    private String model;

    private Integer llmCalls;

    private Long latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Boolean fromCache;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}