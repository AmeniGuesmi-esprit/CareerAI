package com.careerai.experience.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitchResponse {

    /** Version courte (accroche). */
    private String courte;

    /** Version standard (~80 mots, ~30 s a l'oral). */
    private String standard;

    /** Version enthousiaste. */
    private String enthousiaste;
}