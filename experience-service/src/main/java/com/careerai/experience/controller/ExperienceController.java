package com.careerai.experience.controller;

import com.careerai.experience.dto.ApiResponse;
import com.careerai.experience.dto.ExperienceRequest;
import com.careerai.experience.dto.ExperienceResponse;
import com.careerai.experience.security.AuthUser;
import com.careerai.experience.service.ExperienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    /** Liste de MES experiences (les plus recentes d'abord). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExperienceResponse>>> getAll(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.success("Liste des experiences", experienceService.getAll(user.id())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExperienceResponse>> getById(@AuthenticationPrincipal AuthUser user,
                                                                   @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Experience trouvee", experienceService.getById(user.id(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExperienceResponse>> create(@AuthenticationPrincipal AuthUser user,
                                                                  @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Experience ajoutee", experienceService.create(user.id(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExperienceResponse>> update(@AuthenticationPrincipal AuthUser user,
                                                                  @PathVariable Long id,
                                                                  @Valid @RequestBody ExperienceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Experience modifiee", experienceService.update(user.id(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal AuthUser user,
                                                    @PathVariable Long id) {
        experienceService.delete(user.id(), id);
        return ResponseEntity.ok(ApiResponse.success("Experience supprimee", null));
    }
}
