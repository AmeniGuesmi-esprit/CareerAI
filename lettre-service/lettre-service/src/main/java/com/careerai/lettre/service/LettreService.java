package com.careerai.lettre.service;

import com.careerai.lettre.dto.ApiResponse;
import com.careerai.lettre.dto.LettreRequest;
import com.careerai.lettre.dto.LettreResponse;
import com.careerai.lettre.model.Lettre;
import com.careerai.lettre.repository.LettreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LettreService {

    private static final String NOT_FOUND = "Lettre non trouvée";

    private final LettreRepository lettreRepository;

    // ───────────────────────── Utilisateur connecté ─────────────────────────

    @Transactional
    public ApiResponse<LettreResponse> create(String email, LettreRequest request) {
        Lettre lettre = Lettre.builder()
                .userEmail(email)
                .nomEntreprise(request.getNomEntreprise().trim())
                .poste(clean(request.getPoste()))
                .cv(request.getCv().trim())
                .contenu(request.getContenu().trim())
                .build();
        lettreRepository.save(lettre);
        return ApiResponse.success("Lettre créée avec succès", toResponse(lettre));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<LettreResponse>> getMine(String email) {
        List<LettreResponse> lettres = lettreRepository.findByUserEmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
        return ApiResponse.success("Lettres récupérées", lettres);
    }

    @Transactional(readOnly = true)
    public ApiResponse<LettreResponse> getMineById(String email, Long id) {
        return lettreRepository.findByIdAndUserEmail(id, email)
                .map(l -> ApiResponse.success("Lettre récupérée", toResponse(l)))
                .orElse(ApiResponse.error(NOT_FOUND));
    }

    @Transactional
    public ApiResponse<LettreResponse> update(String email, Long id, LettreRequest request) {
        Optional<Lettre> optLettre = lettreRepository.findByIdAndUserEmail(id, email);
        if (optLettre.isEmpty()) return ApiResponse.error(NOT_FOUND);

        Lettre lettre = optLettre.get();
        lettre.setNomEntreprise(request.getNomEntreprise().trim());
        lettre.setPoste(clean(request.getPoste()));
        lettre.setCv(request.getCv().trim());
        lettre.setContenu(request.getContenu().trim());
        lettre.setUpdatedAt(LocalDateTime.now());
        lettreRepository.save(lettre);
        return ApiResponse.success("Lettre mise à jour avec succès", toResponse(lettre));
    }

    @Transactional
    public ApiResponse<Void> delete(String email, Long id) {
        Optional<Lettre> optLettre = lettreRepository.findByIdAndUserEmail(id, email);
        if (optLettre.isEmpty()) return ApiResponse.error(NOT_FOUND);
        lettreRepository.delete(optLettre.get());
        return ApiResponse.success("Lettre supprimée avec succès", null);
    }

    // ───────────────────────── Administration ─────────────────────────

    @Transactional(readOnly = true)
    public ApiResponse<List<LettreResponse>> getAll() {
        List<LettreResponse> lettres = lettreRepository.findAll().stream().map(this::toResponse).toList();
        return ApiResponse.success("Lettres récupérées", lettres);
    }

    @Transactional
    public ApiResponse<Void> deleteById(Long id) {
        if (!lettreRepository.existsById(id)) return ApiResponse.error(NOT_FOUND);
        lettreRepository.deleteById(id);
        return ApiResponse.success("Lettre supprimée", null);
    }

    // ───────────────────────── Utilitaires ─────────────────────────

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LettreResponse toResponse(Lettre l) {
        return LettreResponse.builder()
                .id(l.getId())
                .userEmail(l.getUserEmail())
                .nomEntreprise(l.getNomEntreprise())
                .poste(l.getPoste())
                .cv(l.getCv())
                .contenu(l.getContenu())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
