package com.agente.atencion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(
    boolean enabled,
    String clientId,
    String clientSecret,
    String webhookId,
    String baseUrl,
    String successUrl,
    String cancelUrl
) {
    public boolean credencialesCargadas() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean webhookConfigurado() {
        return webhookId != null && !webhookId.isBlank();
    }
}
