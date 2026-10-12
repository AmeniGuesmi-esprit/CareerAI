package com.careerai.skillgap.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Entité 1 : CV uploadé par l'utilisateur (fichier sur disque + texte extrait). */
@Entity
@Table(name = "cv_uploads", indexes = @Index(columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CvUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id de l'utilisateur connecté (claim userId du JWT). */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String originalFileName;

    /** pdf | docx | txt */
    @Column(nullable = false, length = 10)
    private String fileType;

    /** Nom du fichier sur disque (UUID, jamais le nom fourni par le client). */
    @Column(nullable = false, unique = true)
    private String storedFileName;

    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
