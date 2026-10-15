package com.careerai.skillgap.dto;

import java.time.LocalDateTime;

public record AnalysisResponse(Long id, Long userId, Long cvId, String cvFileName,
                               String jobTitle, String jobDescription,
                               int matchScore, AnalysisResult result,
                               String model, LocalDateTime createdAt) {}
