package com.careerai.interview.session;

import com.careerai.interview.session.SessionDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

import static com.careerai.interview.security.Access.USER_HEADER;

@RestController
@RequestMapping("/api/interview/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @PostMapping
    public ResponseEntity<SessionDetail> create(
            @RequestHeader(USER_HEADER) Long userId,
            @Valid @RequestBody CreateSessionRequest request) {
        SessionDetail created = service.create(userId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/interview/sessions/" + created.id())).body(created);
    }

    @GetMapping
    public Page<SessionSummary> list(
            @RequestHeader(USER_HEADER) Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(userId, pageable);
    }

    @GetMapping("/{id}")
    public SessionDetail get(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return service.detail(userId, id);
    }

    @GetMapping("/{id}/rag")
    public RagView rag(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return service.ragView(userId, id);
    }

    @PatchMapping("/{id}")
    public SessionDetail rename(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateSessionRequest request) {
        return service.rename(userId, id, request.jobTitle());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<SessionDetail> retry(@RequestHeader(USER_HEADER) Long userId, @PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.retry(userId, id));
    }

    @PutMapping("/{id}/questions/{questionId}/answer")
    public ResponseEntity<AnswerView> saveAnswer(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long id,
            @PathVariable Long questionId,
            @Valid @RequestBody AnswerRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.upsertAnswer(userId, id, questionId, request.content()));
    }

    @DeleteMapping("/{id}/questions/{questionId}/answer")
    public ResponseEntity<Void> deleteAnswer(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable Long id,
            @PathVariable Long questionId) {
        service.deleteAnswer(userId, id, questionId);
        return ResponseEntity.noContent().build();
    }
}
