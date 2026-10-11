package com.careerai.interview.session;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String intent;

    @Column(name = "expected_points", nullable = false, columnDefinition = "TEXT")
    private String expectedPoints;

    @Column(name = "source_ids", nullable = false, columnDefinition = "TEXT")
    private String sourceIds;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 100)
    private String model;

    private Integer seed;

    @Column(name = "rag_manifest_hash", length = 64)
    private String ragManifestHash;

    @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    private InterviewAnswer answer;
}
