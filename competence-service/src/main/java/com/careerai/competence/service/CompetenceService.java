package com.careerai.competence.service;

import com.careerai.competence.dto.CompetenceRequest;
import com.careerai.competence.dto.CompetenceResponse;
import com.careerai.competence.exception.ConflictException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.model.Competence;
import com.careerai.competence.repository.CompetenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompetenceService {

    private final CompetenceRepository repository;

    @Transactional(readOnly = true)
    public List<CompetenceResponse> findAll(Long userId) {
        return repository.findByUserIdOrderByNomAsc(userId).stream().map(CompetenceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompetenceResponse findById(Long userId, Long id) {
        return CompetenceResponse.from(getOwned(userId, id));
    }

    @Transactional
    public CompetenceResponse create(Long userId, CompetenceRequest req) {
        String nom = req.getNom().trim();
        if (repository.existsByUserIdAndNomIgnoreCase(userId, nom)) {
            throw new ConflictException("Cette compétence existe déjà");
        }
        Competence c = Competence.builder()
                .userId(userId).nom(nom).niveau(req.getNiveau()).categorie(req.getCategorie()).build();
        return CompetenceResponse.from(repository.save(c));
    }

    @Transactional
    public CompetenceResponse update(Long userId, Long id, CompetenceRequest req) {
        Competence c = getOwned(userId, id);
        String nom = req.getNom().trim();
        if (repository.existsByUserIdAndNomIgnoreCaseAndIdNot(userId, nom, id)) {
            throw new ConflictException("Cette compétence existe déjà");
        }
        c.setNom(nom);
        c.setNiveau(req.getNiveau());
        c.setCategorie(req.getCategorie());
        c.setUpdatedAt(LocalDateTime.now());
        return CompetenceResponse.from(repository.save(c));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(getOwned(userId, id));
    }

    private Competence getOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Compétence introuvable"));
    }
}
