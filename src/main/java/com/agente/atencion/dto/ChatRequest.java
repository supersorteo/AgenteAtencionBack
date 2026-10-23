package com.agente.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "tenantId es obligatorio") String tenantId,
    String sessionId,
    @NotBlank(message = "El mensaje no puede estar vacío")
    @Size(max = 2000, message = "El mensaje no puede superar 2000 caracteres")
    String mensaje
) {}
