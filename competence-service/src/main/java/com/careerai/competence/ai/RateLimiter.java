package com.careerai.competence.ai;

import com.careerai.competence.exception.AiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {

    private final Map<Long, Deque<Long>> hits = new ConcurrentHashMap<>();

    public void check(Long userId, int max, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) queue.pollFirst();
            if (queue.size() >= max) {
                throw new AiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Trop de demandes, attends une minute avant de réessayer.");
            }
            queue.addLast(now);
        }
    }
}
