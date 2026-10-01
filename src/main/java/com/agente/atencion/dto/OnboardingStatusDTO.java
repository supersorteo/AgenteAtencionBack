package com.agente.atencion.dto;

public record OnboardingStatusDTO(
    boolean tourVisto,
    boolean configCompleta,
    boolean tieneServicios,
    boolean tieneBarberos
) {}
