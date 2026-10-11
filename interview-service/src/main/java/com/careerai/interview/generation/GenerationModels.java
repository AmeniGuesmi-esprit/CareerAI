package com.careerai.interview.generation;

import java.util.List;
import java.util.Map;

public final class GenerationModels {

    private GenerationModels() { }

    public record QuestionContext(Long sessionId, String cvText, String jobTitle, String jobDescription,
                                  String track, String level, String compatContext, int questionCount) { }

    public record QuestionDraft(String category, String difficulty, String question, String intent,
                                List<String> expectedPoints, List<String> sourceIds) { }

    public record FeedbackContext(Long answerId, String track, String level, String question, String intent,
                                  String category, List<String> expectedPoints, List<String> sourceIds,
                                  String answerContent, String answerHash) { }

    public record FeedbackDraft(int score, String verdict, List<String> strengths, List<String> gaps,
                                String improvedAnswer, Map<String, String> star, List<String> citedSourceIds) { }

    public record Trace(String promptVersion, String model, int seed, String manifestHash) { }
}
