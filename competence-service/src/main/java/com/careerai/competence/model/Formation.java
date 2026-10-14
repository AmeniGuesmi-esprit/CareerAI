package com.careerai.competence.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "formations", indexes = @Index(name = "idx_formation_user", columnList = "user_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Formation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id de l'utilisateur (user-service), issu du JWT. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String diplome;

    @Column(nullable = false)
    private String etablissement;

    private String domaine;

    @Column(nullable = false)
    private LocalDate dateDebut;

    private LocalDate dateFin;

    @Builder.Default
    private boolean enCours = false;

    @Column(length = 2000)
    private String description;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
