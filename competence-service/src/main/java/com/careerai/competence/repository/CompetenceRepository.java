package com.careerai.competence.repository;

import com.careerai.competence.model.Competence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetenceRepository extends JpaRepository<Competence, Long> {
    List<Competence> findByUserIdOrderByNomAsc(Long userId);
    Optional<Competence> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndNomIgnoreCase(Long userId, String nom);
    boolean existsByUserIdAndNomIgnoreCaseAndIdNot(Long userId, String nom, Long id);
}
