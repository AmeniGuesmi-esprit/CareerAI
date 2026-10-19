package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.SkillGapAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillGapAnalysisRepository extends JpaRepository<SkillGapAnalysis, Long> {
    List<SkillGapAnalysis> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<SkillGapAnalysis> findByIdAndUserId(Long id, Long userId);
    void deleteByCvId(Long cvId);
}
