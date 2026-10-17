package com.careerai.interview.session;

public final class GenerationEvents {

    private GenerationEvents() { }

    public record QuestionsRequested(Long sessionId) { }

    public record FeedbackRequested(Long answerId) { }
}
