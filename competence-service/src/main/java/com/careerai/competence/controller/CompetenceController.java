package com.careerai.competence.controller;

import com.careerai.competence.dto.ApiResponse;
import com.careerai.competence.dto.CompetenceRequest;
import com.careerai.competence.dto.CompetenceResponse;
import com.careerai.competence.service.CompetenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD des compétences de l'utilisateur connecté (via la gateway : /api/competences). */
@RestController
@RequestMapping("/api/competences")
@RequiredArgsConstructor
public class CompetenceController {

    private final CompetenceService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompetenceResponse>>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Compétences récupérées", service.findAll(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompetenceResponse>> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Compétence récupérée", service.findById(userId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompetenceResponse>> create(@AuthenticationPrincipal Long userId,
                                                                  @Valid @RequestBody CompetenceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Compétence ajoutée", service.create(userId, req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompetenceResponse>> update(@AuthenticationPrincipal Long userId,
                                                                  @PathVariable Long id,
                                                                  @Valid @RequestBody CompetenceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Compétence modifiée", service.update(userId, id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Compétence supprimée", null));
    }
}
