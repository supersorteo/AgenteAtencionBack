package com.agente.atencion.dto;

public record ChatRequest(String tenantId, String sessionId, String mensaje) {}
