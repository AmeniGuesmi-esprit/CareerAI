package com.careerai.experience.service;

import com.careerai.experience.dto.ExperienceRequest;
import com.careerai.experience.dto.ExperienceResponse;
import com.careerai.experience.exception.BadRequestException;
import com.careerai.experience.exception.ResourceNotFoundException;
import com.careerai.experience.model.Experience;
import com.careerai.experience.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Toutes les operations sont limitees aux experiences de l'utilisateur connecte (userId issu du JWT). */
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ExperienceRepository repository;

    @Transactional(readOnly = true)
    public List<ExperienceResponse> getAll(Long userId) {
        return repository.findByUserIdOrderByDateDebutDesc(userId).stream()
                .map(ExperienceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExperienceResponse getById(Long userId, Long id) {
        return ExperienceResponse.from(findOwned(userId, id));
    }

    @Transactional
    public ExperienceResponse create(Long userId, ExperienceRequest request) {
        validateDates(request);
        Experience experience = Experience.builder().userId(userId).build();
        apply(experience, request);
        return ExperienceResponse.from(repository.save(experience));
    }

    @Transactional
    public ExperienceResponse update(Long userId, Long id, ExperienceRequest request) {
        validateDates(request);
        Experience experience = findOwned(userId, id);
        apply(experience, request);
        return ExperienceResponse.from(repository.save(experience));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOwned(userId, id));
    }

    // ---------- helpers ----------

    /** 404 aussi quand l'experience appartient a un autre utilisateur (on ne revele pas son existence). */
    private Experience findOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Experience introuvable"));
    }

    private void validateDates(ExperienceRequest r) {
        if (!r.isEnCours() && r.getDateFin() == null) {
            throw new BadRequestException("La date de fin est obligatoire si l'experience n'est pas en cours");
        }
        if (!r.isEnCours() && r.getDateFin().isBefore(r.getDateDebut())) {
            throw new BadRequestException("La date de fin doit etre posterieure a la date de debut");
        }
    }

    private void apply(Experience e, ExperienceRequest r) {
        e.setPoste(r.getPoste().trim());
        e.setEntreprise(r.getEntreprise().trim());
        e.setLieu(r.getLieu() == null || r.getLieu().isBlank() ? null : r.getLieu().trim());
        e.setTypeContrat(r.getTypeContrat());
        e.setDateDebut(r.getDateDebut());
        e.setEnCours(r.isEnCours());
        e.setDateFin(r.isEnCours() ? null : r.getDateFin());
        e.setDescription(r.getDescription() == null || r.getDescription().isBlank() ? null : r.getDescription().trim());
    }
}
