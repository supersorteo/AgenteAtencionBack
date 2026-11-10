package com.agente.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "tenantId es obligatorio") String tenantId,
    @NotBlank(message = "sessionId es obligatorio") @Size(max = 64, message = "sessionId demasiado largo") String sessionId,
    @NotBlank(message = "El mensaje no puede estar vacío")
    @Size(max = 2000, message = "El mensaje no puede superar 2000 caracteres")
    String mensaje
) {}
