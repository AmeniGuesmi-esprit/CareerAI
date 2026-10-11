package com.careerai.interview.session;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interview_sessions", indexes = @Index(name = "idx_session_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @Builder @AllArgsConstructor
public class InterviewSession {

    public enum Status { GENERATING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cv_id")
    private Long cvId;

    @Column(name = "cv_label", nullable = false, length = 150)
    private String cvLabel;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "cv_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String cvText;

    @Column(name = "compat_analysis_id")
    private Long compatAnalysisId;

    @Column(name = "compat_context", columnDefinition = "TEXT")
    private String compatContext;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(nullable = false, length = 40)
    private String track;

    @Column(nullable = false, length = 20)
    private String level;

    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills;

    @Column(name = "gap_skills", columnDefinition = "TEXT")
    private String gapSkills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<InterviewQuestion> questions = new ArrayList<>();

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
