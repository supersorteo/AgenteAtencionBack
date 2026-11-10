package com.agente.atencion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pagos")
public record PagosProperties(String backendUrl) {}
