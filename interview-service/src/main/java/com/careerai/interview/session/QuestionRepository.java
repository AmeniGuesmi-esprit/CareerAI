package com.careerai.interview.session;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<InterviewQuestion, Long> {
}
