package com.careerai.lettre.controller;

import com.careerai.lettre.dto.ApiResponse;
import com.careerai.lettre.dto.LettreRequest;
import com.careerai.lettre.dto.LettreResponse;
import com.careerai.lettre.service.LettreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lettres")
@RequiredArgsConstructor
public class LettreController {

    private final LettreService lettreService;

    // ── Utilisateur connecté (USER ou ADMIN) : uniquement ses propres lettres ──
    @PostMapping
    public ResponseEntity<ApiResponse<LettreResponse>> create(Authentication auth,
                                                              @Valid @RequestBody LettreRequest request) {
        return ResponseEntity.ok(lettreService.create(auth.getName(), request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LettreResponse>>> getMine(Authentication auth) {
        return ResponseEntity.ok(lettreService.getMine(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LettreResponse>> getById(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(lettreService.getMineById(auth.getName(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LettreResponse>> update(Authentication auth,
                                                              @PathVariable Long id,
                                                              @Valid @RequestBody LettreRequest request) {
        return ResponseEntity.ok(lettreService.update(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(lettreService.delete(auth.getName(), id));
    }

    // ── Administration (ADMIN uniquement) ──
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<LettreResponse>>> getAll() {
        return ResponseEntity.ok(lettreService.getAll());
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAsAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(lettreService.deleteById(id));
    }
}
