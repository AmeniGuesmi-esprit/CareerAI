package com.careerai.interview.session;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<InterviewSession, Long> {

    Page<InterviewSession> findByUserId(Long userId, Pageable pageable);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update InterviewSession s set s.status = :failed, s.errorCode = 'INTERRUPTED' where s.status = :generating")
    int failInterruptedSessions(@Param("generating") InterviewSession.Status generating,
                                @Param("failed") InterviewSession.Status failed);
}
