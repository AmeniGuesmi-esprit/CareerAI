package com.careerai.interview.session;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_feedbacks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterviewFeedback {

    public enum Status { PENDING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false, unique = true)
    private InterviewAnswer answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String verdict;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String gaps;

    @Column(name = "improved_answer", columnDefinition = "TEXT")
    private String improvedAnswer;

    @Column(columnDefinition = "TEXT")
    private String star;

    @Column(name = "cited_source_ids", columnDefinition = "TEXT")
    private String citedSourceIds;

    @Column(name = "answer_hash", nullable = false, length = 64)
    private String answerHash;

    @Column(name = "prompt_version", length = 40)
    private String promptVersion;

    @Column(length = 100)
    private String model;

    private Integer seed;

    @Column(name = "rag_manifest_hash", length = 64)
    private String ragManifestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
