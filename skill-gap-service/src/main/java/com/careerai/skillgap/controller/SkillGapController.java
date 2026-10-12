package com.careerai.skillgap.controller;

import com.careerai.skillgap.dto.*;
import com.careerai.skillgap.service.CvService;
import com.careerai.skillgap.service.SkillGapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** L'id utilisateur (@AuthenticationPrincipal) provient toujours du JWT, jamais du client. */
@RestController
@RequestMapping("/api/skill-gap")
@RequiredArgsConstructor
public class SkillGapController {

    private final CvService cvService;
    private final SkillGapService skillGapService;

    // ── CV uploadés ──

    @PostMapping(value = "/cvs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CvUploadResponse>> upload(@AuthenticationPrincipal Long userId,
                                                                @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CV importé", cvService.upload(userId, file)));
    }

    @GetMapping("/cvs")
    public ApiResponse<List<CvUploadResponse>> myCvs(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success("OK", cvService.listMine(userId));
    }

    @DeleteMapping("/cvs/{id}")
    public ApiResponse<Void> deleteCv(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        cvService.delete(userId, id);
        return ApiResponse.success("CV supprimé", null);
    }

    // ── Analyses ──

    @PostMapping("/analyses")
    public ResponseEntity<ApiResponse<AnalysisResponse>> analyze(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody AnalysisCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Analyse terminée", skillGapService.analyze(userId, request)));
    }

    @GetMapping("/analyses")
    public ApiResponse<List<AnalysisResponse>> myAnalyses(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success("OK", skillGapService.listMine(userId));
    }

    @GetMapping("/analyses/{id}")
    public ApiResponse<AnalysisResponse> getAnalysis(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return ApiResponse.success("OK", skillGapService.getMine(userId, id));
    }

    @DeleteMapping("/analyses/{id}")
    public ApiResponse<Void> deleteAnalysis(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        skillGapService.deleteMine(userId, id);
        return ApiResponse.success("Analyse supprimée", null);
    }
}
