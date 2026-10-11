package com.careerai.interview.generation;

import com.careerai.interview.session.GenerationEvents.FeedbackRequested;
import com.careerai.interview.session.GenerationEvents.QuestionsRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GenerationListener {

    private final QuestionGenerationService questions;
    private final FeedbackService feedback;

    @Async("generationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestionsRequested(QuestionsRequested event) {
        questions.generate(event.sessionId());
    }

    @Async("generationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackRequested(FeedbackRequested event) {
        feedback.generate(event.answerId());
    }
}
