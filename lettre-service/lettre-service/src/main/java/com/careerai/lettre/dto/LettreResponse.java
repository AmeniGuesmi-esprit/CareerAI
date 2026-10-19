package com.careerai.lettre.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class LettreResponse {
    private Long id;
    private String userEmail;
    private String nomEntreprise;
    private String poste;
    private String cv;
    private String contenu;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
