package com.careerai.interview.generation;

import com.careerai.interview.session.FeedbackRepository;
import com.careerai.interview.session.InterviewFeedback;
import com.careerai.interview.session.InterviewSession;
import com.careerai.interview.session.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationRecovery {

    private final SessionRepository sessions;
    private final FeedbackRepository feedbacks;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterrupted() {
        int s = sessions.failInterruptedSessions(InterviewSession.Status.GENERATING, InterviewSession.Status.FAILED);
        int f = feedbacks.failInterruptedFeedbacks(InterviewFeedback.Status.PENDING, InterviewFeedback.Status.FAILED);
        if (s + f > 0) {
            log.warn("{} session(s) et {} feedback(s) interrompus au démarrage : marqués INTERRUPTED", s, f);
        }
    }
}
