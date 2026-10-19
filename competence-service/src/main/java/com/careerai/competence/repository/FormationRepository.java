package com.careerai.competence.repository;

import com.careerai.competence.model.Formation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormationRepository extends JpaRepository<Formation, Long> {
    List<Formation> findByUserIdOrderByDateDebutDesc(Long userId);
    Optional<Formation> findByIdAndUserId(Long id, Long userId);
}
