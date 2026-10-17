package com.careerai.lettre.dto;

/** Fournisseur IA proposé dans l'interface. */
public record AiProviderInfo(String name, String model, boolean configured, boolean preferred) {}
