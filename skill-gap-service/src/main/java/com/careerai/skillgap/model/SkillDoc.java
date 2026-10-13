package com.careerai.skillgap.model;

import jakarta.persistence.*;
import lombok.*;

/** Fiche de la base de connaissances RAG : une compétence, son parcours d'apprentissage et son vecteur (embedding). */
@Entity
@Table(name = "skill_docs", uniqueConstraints = @UniqueConstraint(columnNames = "slug"))
@Getter @Setter @NoArgsConstructor
public class SkillDoc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant stable de la fiche (ex. "docker"), sert à la mise à jour depuis le fichier JSON. */
    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false)
    private String skill;

    private String category;

    /** Texte de la fiche : description, parcours, ressources. Il est vectorisé puis inséré dans le prompt. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Vecteur d'embedding au format JSON ([0.12,-0.87,...]), null tant que la fiche n'est pas indexée. */
    @Column(columnDefinition = "LONGTEXT")
    private String embedding;
}
