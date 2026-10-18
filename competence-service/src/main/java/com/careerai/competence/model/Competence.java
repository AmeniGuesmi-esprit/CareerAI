package com.careerai.competence.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "competences",
        uniqueConstraints = @UniqueConstraint(name = "uk_competence_user_nom", columnNames = {"user_id", "nom"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Competence {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id de l'utilisateur (user-service), issu du JWT. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NiveauCompetence niveau;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieCompetence categorie;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
