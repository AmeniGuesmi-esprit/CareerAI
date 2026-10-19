package com.careerai.lettre.controller;

import com.careerai.lettre.dto.*;
import com.careerai.lettre.service.LettreAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Assistant IA : génération et amélioration de lettres. Ces endpoints n'enregistrent rien. */
@RestController
@RequestMapping("/api/lettres/ai")
@RequiredArgsConstructor
public class LettreAiController {

    private final LettreAiService aiService;

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<AiProviderInfo>>> providers() {
        return ResponseEntity.ok(ApiResponse.success("Fournisseurs IA", aiService.providers()));
    }

    @PostMapping(value = "/extract-cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<String>> extractCv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("CV lu avec succès", aiService.extractCv(file)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<AiLetterResponse>> generate(Authentication auth,
                                                                  @Valid @RequestBody GenerateLetterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lettre générée", aiService.generate(auth.getName(), request)));
    }

    @PostMapping("/improve")
    public ResponseEntity<ApiResponse<AiLetterResponse>> improve(Authentication auth,
                                                                 @Valid @RequestBody ImproveLetterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Lettre améliorée", aiService.improve(auth.getName(), request)));
    }
}
