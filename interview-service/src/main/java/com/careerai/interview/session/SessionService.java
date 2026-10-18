package com.careerai.interview.session;

import com.careerai.interview.config.InterviewConfig.InterviewProperties;
import com.careerai.interview.cv.CvDocument;
import com.careerai.interview.cv.CvService;
import com.careerai.interview.exception.ApiException;
import com.careerai.interview.rag.QueryPlanner;
import com.careerai.interview.rag.QueryPlanner.QueryPlan;
import com.careerai.interview.rag.RagIndex;
import com.careerai.interview.rag.RagTraces;
import com.careerai.interview.session.CompatAnalysisClient.CompatAnalysis;
import com.careerai.interview.session.GenerationEvents.FeedbackRequested;
import com.careerai.interview.session.GenerationEvents.QuestionsRequested;
import com.careerai.interview.session.SessionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessions;
    private final AnswerRepository answers;
    private final FeedbackRepository feedbacks;
    private final CvService cvService;
    private final CompatAnalysisClient compat;
    private final QueryPlanner planner;
    private final RagIndex index;
    private final JsonCodec json;
    private final InterviewProperties props;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager txManager;

    public SessionDetail create(Long userId, CreateSessionRequest req) {
        int count = req.questionCount() == null ? props.questionCountDefault() : req.questionCount();
        if (count < props.questionCountMin() || count > props.questionCountMax()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "questionCount doit être entre " + props.questionCountMin() + " et " + props.questionCountMax());
        }
        CvDocument cv = cvService.find(userId, req.cvId());
        CompatAnalysis compatContext = req.analysisId() == null ? null : compat.fetch(userId, req.analysisId());
        QueryPlan plan = planner.plan(req.jobTitle(), req.jobDescription(), cv.getAnonymizedText(),
                index.snapshot().vocabulary());
        InterviewSession session = InterviewSession.builder()
                .userId(userId)
                .cvId(cv.getId())
                .cvLabel(cv.getLabel())
                .jobTitle(req.jobTitle().trim())
                .jobDescription(req.jobDescription().trim())
                .cvText(cv.getAnonymizedText())
                .compatAnalysisId(req.analysisId())
                .compatContext(json.write(compatContext))
                .questionCount(count)
                .track(plan.track())
                .level(plan.level())
                .matchedSkills(json.write(plan.matchedSkills()))
                .gapSkills(json.write(plan.gapSkills()))
                .status(InterviewSession.Status.GENERATING)
                .build();
        return new TransactionTemplate(txManager).execute(status -> {
            InterviewSession saved = sessions.save(session);
            events.publishEvent(new QuestionsRequested(saved.getId()));
            return toDetail(saved);
        });
    }

    @Transactional(readOnly = true)
    public Page<SessionSummary> list(Long userId, Pageable pageable) {
        return sessions.findByUserId(userId, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(Long userId, Long id) {
        return toDetail(find(userId, id));
    }

    @Transactional
    public SessionDetail rename(Long userId, Long id, String jobTitle) {
        InterviewSession session = find(userId, id);
        session.setJobTitle(jobTitle.trim());
        return toDetail(session);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        sessions.delete(find(userId, id));
    }

    @Transactional
    public SessionDetail retry(Long userId, Long id) {
        InterviewSession session = find(userId, id);
        if (session.getStatus() != InterviewSession.Status.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_FAILED", "Seules les sessions en échec peuvent être relancées.");
        }
        session.getQuestions().clear();
        session.setStatus(InterviewSession.Status.GENERATING);
        session.setErrorCode(null);
        events.publishEvent(new QuestionsRequested(session.getId()));
        return toDetail(session);
    }

    @Transactional
    public AnswerView upsertAnswer(Long userId, Long sessionId, Long questionId, String content) {
        InterviewSession session = find(userId, sessionId);
        if (session.getStatus() != InterviewSession.Status.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_READY", "Les questions ne sont pas encore prêtes.");
        }
        InterviewQuestion question = findQuestion(session, questionId);
        String text = content.trim();
        String hash = sha256(text);
        InterviewAnswer answer = question.getAnswer();
        if (answer == null) {
            answer = answers.save(InterviewAnswer.builder().question(question).content(text).answerHash(hash).build());
            question.setAnswer(answer);
        } else {
            if (hash.equals(answer.getAnswerHash()) && answer.getFeedback() != null
                    && answer.getFeedback().getStatus() == InterviewFeedback.Status.READY) {
                return toAnswerView(answer);
            }
            answer.setContent(text);
            answer.setAnswerHash(hash);
        }
        InterviewFeedback feedback = answer.getFeedback();
        if (feedback == null) {
            feedback = feedbacks.save(InterviewFeedback.builder().answer(answer).status(InterviewFeedback.Status.PENDING)
                    .answerHash(hash).build());
            answer.setFeedback(feedback);
        } else {
            feedback.setStatus(InterviewFeedback.Status.PENDING);
            feedback.setErrorCode(null);
            feedback.setAnswerHash(hash);
        }
        events.publishEvent(new FeedbackRequested(answer.getId()));
        return toAnswerView(answer);
    }

    @Transactional
    public void deleteAnswer(Long userId, Long sessionId, Long questionId) {
        InterviewSession session = find(userId, sessionId);
        InterviewQuestion question = findQuestion(session, questionId);
        InterviewAnswer answer = question.getAnswer();
        if (answer != null) {
            question.setAnswer(null);
        }
    }

    private InterviewSession find(Long userId, Long id) {
        return sessions.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session introuvable : " + id));
    }

    private InterviewQuestion findQuestion(InterviewSession session, Long questionId) {
        return session.getQuestions().stream().filter(q -> q.getId().equals(questionId)).findFirst().orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question introuvable : " + questionId));
    }

    private SessionSummary toSummary(InterviewSession s) {
        int answered = (int) s.getQuestions().stream().filter(q -> q.getAnswer() != null).count();
        return new SessionSummary(s.getId(), s.getJobTitle(), s.getCvLabel(), s.getStatus(), s.getQuestionCount(),
                answered, s.getTrack(), s.getLevel(), s.getCreatedAt());
    }

    private SessionDetail toDetail(InterviewSession s) {
        List<QuestionView> views = s.getQuestions().stream().map(this::toQuestionView).toList();
        return new SessionDetail(s.getId(), s.getJobTitle(), s.getJobDescription(), s.getCvLabel(), s.getStatus(),
                s.getErrorCode(), s.getTrack(), s.getLevel(), json.strings(s.getMatchedSkills()),
                json.strings(s.getGapSkills()), s.getCompatAnalysisId(), s.getQuestionCount(), s.getCreatedAt(),
                s.getUpdatedAt(), views);
    }

    private QuestionView toQuestionView(InterviewQuestion q) {
        AnswerView answer = q.getAnswer() == null ? null : toAnswerView(q.getAnswer());
        boolean ready = answer != null && answer.feedback() != null
                && answer.feedback().status() == InterviewFeedback.Status.READY;
        return new QuestionView(q.getId(), q.getPosition(), q.getCategory(), q.getDifficulty(), q.getQuestion(),
                q.getIntent(), ready ? json.strings(q.getExpectedPoints()) : List.of(), answer);
    }

    private AnswerView toAnswerView(InterviewAnswer a) {
        InterviewFeedback f = a.getFeedback();
        FeedbackView fv = f == null ? null : new FeedbackView(f.getStatus(), f.getErrorCode(), f.getScore(), f.getVerdict(),
                json.strings(f.getStrengths()), json.strings(f.getGaps()), f.getImprovedAnswer(),
                f.getStar() == null ? null : json.map(f.getStar()), json.strings(f.getCitedSourceIds()),
                f.getModel(), f.getPromptVersion(), f.getUpdatedAt(),
                json.list(f.getRetrievedSources(), RagTraces.Source.class));
        return new AnswerView(a.getId(), a.getContent(), a.getUpdatedAt(), fv);
    }

    @Transactional(readOnly = true)
    public RagView ragView(Long userId, Long id) {
        InterviewSession s = find(userId, id);
        RagTraces.Trace t = json.object(s.getRagTrace(), RagTraces.Trace.class);
        if (t == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RAG_TRACE_MISSING", "Pas encore de trace RAG pour cette session.");
        }
        List<RagQuestionView> questions = s.getQuestions().stream()
                .map(q -> new RagQuestionView(q.getId(), q.getPosition(), q.getQuestion(), json.strings(q.getSourceIds())))
                .toList();
        return new RagView(t.track(), t.level(), t.mode(), t.matchedSkills(), t.gapSkills(), t.queries(), t.hits(),
                t.promptVersion(), t.model(), t.seed(), t.manifestHash(), questions);
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
