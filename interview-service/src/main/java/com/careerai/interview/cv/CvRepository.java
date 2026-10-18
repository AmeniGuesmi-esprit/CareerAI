package com.careerai.interview.cv;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvRepository extends JpaRepository<CvDocument, Long> {

    Page<CvDocument> findByUserId(Long userId, Pageable pageable);

    Optional<CvDocument> findByIdAndUserId(Long id, Long userId);
}
