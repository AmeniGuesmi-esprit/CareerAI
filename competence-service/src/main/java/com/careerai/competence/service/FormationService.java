package com.careerai.competence.service;

import com.careerai.competence.dto.FormationRequest;
import com.careerai.competence.dto.FormationResponse;
import com.careerai.competence.exception.BadRequestException;
import com.careerai.competence.exception.ResourceNotFoundException;
import com.careerai.competence.model.Formation;
import com.careerai.competence.repository.FormationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormationService {

    private final FormationRepository repository;

    @Transactional(readOnly = true)
    public List<FormationResponse> findAll(Long userId) {
        return repository.findByUserIdOrderByDateDebutDesc(userId).stream().map(FormationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FormationResponse findById(Long userId, Long id) {
        return FormationResponse.from(getOwned(userId, id));
    }

    @Transactional
    public FormationResponse create(Long userId, FormationRequest req) {
        validateDates(req);
        Formation f = Formation.builder().userId(userId).build();
        apply(f, req);
        return FormationResponse.from(repository.save(f));
    }

    @Transactional
    public FormationResponse update(Long userId, Long id, FormationRequest req) {
        validateDates(req);
        Formation f = getOwned(userId, id);
        apply(f, req);
        f.setUpdatedAt(LocalDateTime.now());
        return FormationResponse.from(repository.save(f));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(getOwned(userId, id));
    }

    private Formation getOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Formation introuvable"));
    }

    private void validateDates(FormationRequest req) {
        if (req.isEnCours() && req.getDateFin() != null) {
            throw new BadRequestException("Une formation en cours ne peut pas avoir de date de fin");
        }
        if (!req.isEnCours() && req.getDateFin() == null) {
            throw new BadRequestException("La date de fin est obligatoire si la formation n'est pas en cours");
        }
        if (req.getDateFin() != null && req.getDateFin().isBefore(req.getDateDebut())) {
            throw new BadRequestException("La date de fin doit être postérieure à la date de début");
        }
    }

    private void apply(Formation f, FormationRequest req) {
        f.setDiplome(req.getDiplome().trim());
        f.setEtablissement(req.getEtablissement().trim());
        f.setDomaine(req.getDomaine() == null || req.getDomaine().isBlank() ? null : req.getDomaine().trim());
        f.setDateDebut(req.getDateDebut());
        f.setDateFin(req.isEnCours() ? null : req.getDateFin());
        f.setEnCours(req.isEnCours());
        f.setDescription(req.getDescription());
    }
}
