package com.careerai.experience.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "experiences", indexes = @Index(name = "idx_experiences_user_id", columnList = "userId"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Experience {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id de l'utilisateur proprietaire (vient du JWT, jamais du corps de la requete). */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String poste;

    @Column(nullable = false, length = 150)
    private String entreprise;

    @Column(length = 150)
    private String lieu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeContrat typeContrat;

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

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
