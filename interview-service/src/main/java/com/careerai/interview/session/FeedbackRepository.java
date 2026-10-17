package com.careerai.interview.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    @Modifying
    @Query("update InterviewFeedback f set f.status = :failed, f.errorCode = 'INTERRUPTED' where f.status = :pending")
    int failInterruptedFeedbacks(@Param("pending") InterviewFeedback.Status pending,
                                 @Param("failed") InterviewFeedback.Status failed);
}
