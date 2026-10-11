package com.careerai.interview.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.careerai.interview.rag.RagTraces;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SessionDtos {

    private SessionDtos() { }

    public record CreateSessionRequest(
            @NotNull Long cvId,
            @NotBlank @Size(min = 2, max = 150) String jobTitle,
            @NotBlank @Size(min = 50, max = 8000) String jobDescription,
            @Min(3) @Max(10) Integer questionCount,
            Long analysisId) { }

    public record UpdateSessionRequest(@NotBlank @Size(min = 2, max = 150) String jobTitle) { }

    public record AnswerRequest(@NotBlank @Size(max = 5000) String content) { }

    public record SessionSummary(Long id, String jobTitle, String cvLabel, InterviewSession.Status status,
                                 int questionCount, int answeredCount, String track, String level,
                                 LocalDateTime createdAt) { }

    public record SessionDetail(Long id, String jobTitle, String jobDescription, String cvLabel,
                                InterviewSession.Status status, String errorCode, String track, String level,
                                List<String> matchedSkills, List<String> gapSkills, Long compatAnalysisId,
                                int questionCount, LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<QuestionView> questions) { }

    public record QuestionView(Long id, int position, String category, String difficulty, String question,
                               String intent, List<String> expectedPoints, AnswerView answer) { }

    public record AnswerView(Long id, String content, LocalDateTime updatedAt, FeedbackView feedback) { }

    public record FeedbackView(InterviewFeedback.Status status, String errorCode, Integer score, String verdict,
                               List<String> strengths, List<String> gaps, String improvedAnswer,
                               Map<String, String> star, List<String> citedSourceIds, String model,
                               String promptVersion, LocalDateTime updatedAt,
                               List<RagTraces.Source> sources) { }

    public record RagQuestionView(Long questionId, int position, String question, List<String> usedCitationIds) { }

    public record RagView(String track, String level, String mode, List<String> matchedSkills,
                          List<String> gapSkills, List<String> queries, List<RagTraces.Hit> retrieved,
                          String promptVersion, String model, Integer seed, String manifestHash,
                          List<RagQuestionView> questions) { }
}
