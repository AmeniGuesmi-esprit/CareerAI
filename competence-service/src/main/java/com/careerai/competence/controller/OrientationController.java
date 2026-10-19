package com.careerai.competence.controller;

import com.careerai.competence.ai.AdvisorService;
import com.careerai.competence.dto.AdviceRequest;
import com.careerai.competence.dto.AdviceResponse;
import com.careerai.competence.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orientation")
@RequiredArgsConstructor
public class OrientationController {

    private final AdvisorService service;

    @PostMapping("/advice")
    public ResponseEntity<ApiResponse<AdviceResponse>> advise(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody AdviceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Conseil généré", service.advise(userId, req)));
    }
}
