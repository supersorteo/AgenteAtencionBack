package com.agente.atencion.dto;

import java.time.LocalDate;

public record TenantResumen(
    String slug,
    String nombre,
    boolean activo,
    String adminUsername,
    LocalDate fechaRegistro,
    LocalDate fechaVencimientoSuscripcion
) {}
