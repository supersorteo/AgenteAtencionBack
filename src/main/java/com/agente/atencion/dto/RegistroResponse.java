package com.agente.atencion.dto;

public record RegistroResponse(
    String slug,
    String username,
    String panelUrl,
    String mensaje
) {}
