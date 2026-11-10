package com.agente.atencion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(
    boolean enabled,
    String baseUrl,
    String accessToken,
    String webhookSecret,
    String successUrl,
    String failureUrl,
    String pendingUrl
) {
    public boolean credencialesCargadas() {
        return accessToken != null && !accessToken.isBlank();
    }

    public boolean webhookConfigurado() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }
}
