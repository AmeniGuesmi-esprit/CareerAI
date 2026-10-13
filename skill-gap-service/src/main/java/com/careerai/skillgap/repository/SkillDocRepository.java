package com.careerai.skillgap.repository;

import com.careerai.skillgap.model.SkillDoc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillDocRepository extends JpaRepository<SkillDoc, Long> {
    Optional<SkillDoc> findBySlug(String slug);
    List<SkillDoc> findByEmbeddingIsNull();
    List<SkillDoc> findByEmbeddingIsNotNull();
}
