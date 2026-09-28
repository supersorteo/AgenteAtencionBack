package com.agente.atencion.dto;

public record RegistroRequest(
    String nombreNegocio,
    String adminNombre,
    String username,
    String password,
    String codigoInvitacion
) {}
