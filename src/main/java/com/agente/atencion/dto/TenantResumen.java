package com.agente.atencion.dto;

public record TenantResumen(
    String slug,
    String nombre,
    boolean activo,
    String adminUsername
) {}
