package com.careerai.lettre.repository;

import com.careerai.lettre.model.Lettre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LettreRepository extends JpaRepository<Lettre, Long> {

    List<Lettre> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<Lettre> findByIdAndUserEmail(Long id, String userEmail);
}
