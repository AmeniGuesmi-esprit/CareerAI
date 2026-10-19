package com.careerai.compatibility_service.repository;

import com.careerai.compatibility_service.model.Analysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Page<Analysis> findByUserId(Long userId, Pageable pageable);

    Optional<Analysis> findByIdAndUserId(Long id, Long userId);
}