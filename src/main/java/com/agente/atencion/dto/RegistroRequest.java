package com.agente.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
    @NotBlank(message = "El nombre del negocio es obligatorio")
    String nombreNegocio,

    @NotBlank(message = "El nombre del administrador es obligatorio")
    String adminNombre,

    @NotBlank(message = "El usuario es obligatorio")
    @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
    String username,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    String password,

    String codigoInvitacion
) {}
