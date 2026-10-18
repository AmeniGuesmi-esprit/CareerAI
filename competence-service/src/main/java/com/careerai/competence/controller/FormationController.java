package com.careerai.competence.controller;

import com.careerai.competence.dto.ApiResponse;
import com.careerai.competence.dto.FormationRequest;
import com.careerai.competence.dto.FormationResponse;
import com.careerai.competence.service.FormationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD des formations de l'utilisateur connecté (via la gateway : /api/formations). */
@RestController
@RequestMapping("/api/formations")
@RequiredArgsConstructor
public class FormationController {

    private final FormationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FormationResponse>>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Formations récupérées", service.findAll(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FormationResponse>> get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Formation récupérée", service.findById(userId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FormationResponse>> create(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody FormationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Formation ajoutée", service.create(userId, req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FormationResponse>> update(@AuthenticationPrincipal Long userId,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody FormationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Formation modifiée", service.update(userId, id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Formation supprimée", null));
    }
}
