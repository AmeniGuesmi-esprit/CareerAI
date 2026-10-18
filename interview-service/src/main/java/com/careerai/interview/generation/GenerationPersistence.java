package com.careerai.interview.generation;

import com.careerai.interview.generation.GenerationModels.*;
import com.careerai.interview.rag.RagManifestRepository;
import com.careerai.interview.rag.RagTraces;
import com.careerai.interview.session.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GenerationPersistence {

    private final SessionRepository sessions;
    private final AnswerRepository answers;
    private final FeedbackRepository feedbacks;
    private final RagManifestRepository manifests;
    private final JsonCodec json;

    @Transactional(readOnly = true)
    public Optional<QuestionContext> questionContext(Long sessionId) {
        return sessions.findById(sessionId).map(s -> new QuestionContext(s.getId(), s.getCvText(), s.getJobTitle(),
                s.getJobDescription(), s.getTrack(), s.getLevel(), s.getCompatContext(), s.getQuestionCount()));
    }

    @Transactional
    public void saveQuestions(Long sessionId, List<QuestionDraft> drafts, Trace trace, RagTraces.Trace rag) {
        sessions.findById(sessionId).ifPresent(session -> {
            session.getQuestions().clear();
            session.setRagTrace(json.write(rag));
            int position = 1;
            for (QuestionDraft d : drafts) {
                session.getQuestions().add(InterviewQuestion.builder()
                        .session(session)
                        .position(position++)
                        .category(d.category())
                        .difficulty(d.difficulty())
                        .question(d.question())
                        .intent(d.intent())
                        .expectedPoints(json.write(d.expectedPoints()))
                        .sourceIds(json.write(d.sourceIds()))
                        .promptVersion(trace.promptVersion())
                        .model(trace.model())
                        .seed(trace.seed())
                        .ragManifestHash(trace.manifestHash())
                        .build());
            }
            session.setStatus(InterviewSession.Status.READY);
            session.setErrorCode(null);
            sessions.save(session);
        });
    }

    @Transactional
    public void failSession(Long sessionId, String code) {
        sessions.findById(sessionId).ifPresent(session -> {
            session.setStatus(InterviewSession.Status.FAILED);
            session.setErrorCode(code);
            sessions.save(session);
        });
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackContext> feedbackContext(Long answerId) {
        return answers.findById(answerId).map(a -> {
            InterviewQuestion q = a.getQuestion();
            return new FeedbackContext(a.getId(), q.getSession().getTrack(), q.getSession().getLevel(),
                    q.getQuestion(), q.getIntent(), q.getCategory(), json.strings(q.getExpectedPoints()),
                    json.strings(q.getSourceIds()), a.getContent(), a.getAnswerHash());
        });
    }

    @Transactional
    public void saveFeedback(Long answerId, String expectedHash, FeedbackDraft d, Trace trace,
                             List<RagTraces.Source> retrieved) {
        answers.findById(answerId).filter(a -> expectedHash.equals(a.getAnswerHash())).ifPresent(a -> {
            InterviewFeedback f = a.getFeedback();
            if (f == null) {
                return;
            }
            f.setRetrievedSources(json.write(retrieved));
            f.setStatus(InterviewFeedback.Status.READY);
            f.setErrorCode(null);
            f.setScore(d.score());
            f.setVerdict(d.verdict());
            f.setStrengths(json.write(d.strengths()));
            f.setGaps(json.write(d.gaps()));
            f.setImprovedAnswer(d.improvedAnswer());
            f.setStar(json.write(d.star()));
            f.setCitedSourceIds(json.write(d.citedSourceIds()));
            f.setPromptVersion(trace.promptVersion());
            f.setModel(trace.model());
            f.setSeed(trace.seed());
            f.setRagManifestHash(trace.manifestHash());
            feedbacks.save(f);
        });
    }

    @Transactional
    public void failFeedback(Long answerId, String expectedHash, String code) {
        answers.findById(answerId).filter(a -> expectedHash.equals(a.getAnswerHash())).ifPresent(a -> {
            InterviewFeedback f = a.getFeedback();
            if (f != null) {
                f.setStatus(InterviewFeedback.Status.FAILED);
                f.setErrorCode(code);
                feedbacks.save(f);
            }
        });
    }

    public String manifestHash() {
        return manifests.findById(1).map(m -> m.getCorpusHash()).orElse(null);
    }
}
