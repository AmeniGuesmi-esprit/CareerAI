package com.careerai.lettre.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lettres", indexes = @Index(name = "idx_lettre_user_email", columnList = "userEmail"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Lettre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Propriétaire : email de l'utilisateur (claim du JWT émis par le user-service). */
    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String nomEntreprise;

    private String poste;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String cv;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenu;

    @Builder.Default private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
