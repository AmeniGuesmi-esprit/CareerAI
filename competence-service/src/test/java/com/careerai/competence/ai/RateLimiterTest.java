package com.careerai.competence.ai;

import com.careerai.competence.exception.AiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToMaxRequests() {
        assertThatCode(() -> {
            for (int i = 0; i < 3; i++) limiter.check(1L, 3, 60_000);
        }).doesNotThrowAnyException();
    }

    @Test
    void blocksWhenMaxExceeded_with429() {
        for (int i = 0; i < 3; i++) limiter.check(1L, 3, 60_000);

        assertThatThrownBy(() -> limiter.check(1L, 3, 60_000))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void limitIsPerUser() {
        limiter.check(1L, 1, 60_000);

        assertThatCode(() -> limiter.check(2L, 1, 60_000)).doesNotThrowAnyException();
    }

    @Test
    void windowExpires_allowsAgain() throws InterruptedException {
        limiter.check(1L, 1, 50);
        Thread.sleep(80);

        assertThatCode(() -> limiter.check(1L, 1, 50)).doesNotThrowAnyException();
    }
}
