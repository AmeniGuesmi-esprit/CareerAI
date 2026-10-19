package com.careerai.compatibility_service.controller;

import com.careerai.compatibility_service.dto.AnalysisDtos.*;
import com.careerai.compatibility_service.service.AnalysisService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/compatibility/analyses")
@RequiredArgsConstructor
@Validated
@Tag(name = "Analyses de compatibilité CV / offre")
public class AnalysisController {

    private static final String USER_HEADER = "X-User-Id";

    private final AnalysisService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResponse> create(
            @RequestHeader(value = USER_HEADER, defaultValue = "1") Long userId,
            @RequestParam("cv") MultipartFile cv,
            @RequestParam("jobTitle") @NotBlank @Size(max = 150) String jobTitle,
            @RequestParam("jobDescription") @NotBlank @Size(min = 50, max = 8000) String jobDescription) {
        AnalysisResponse created = service.create(userId, cv, jobTitle, jobDescription);
        return ResponseEntity.created(URI.create("/api/compatibility/analyses/" + created.id())).body(created);
    }

    @GetMapping
    public Page<AnalysisListItem> list(
            @RequestHeader(value = USER_HEADER, defaultValue = "1") Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(userId, pageable);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(
            @RequestHeader(value = USER_HEADER, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        return service.get(userId, id);
    }

    @PatchMapping("/{id}")
    public AnalysisResponse rename(
            @RequestHeader(value = USER_HEADER, defaultValue = "1") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RenameRequest request) {
        return service.rename(userId, id, request.jobTitle());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = USER_HEADER, defaultValue = "1") Long userId,
            @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}