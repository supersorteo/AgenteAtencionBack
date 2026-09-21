package com.agente.atencion.security;

public record UsuarioAutenticado(String username, String rol, String tenantId, Long barberoId) {}
