package com.careerai.experience.repository;

import com.careerai.experience.model.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByUserIdOrderByDateDebutDesc(Long userId);

    Optional<Experience> findByIdAndUserId(Long id, Long userId);
}
