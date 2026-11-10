package com.agente.atencion.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagosPropertiesTest {

    private StandardEnvironment entorno(String archivo, Map<String, Object> variables) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        env.getPropertySources().addLast(new ResourcePropertySource("classpath:" + archivo));
        return env;
    }

    @Test
    void local_sinVariables_usaDefaultsDeDesarrollo() throws IOException {
        StandardEnvironment env = entorno("application.properties", Map.of());

        assertEquals("true", env.getProperty("mercadopago.enabled"));
        assertEquals("https://api.mercadopago.com", env.getProperty("mercadopago.base-url"));
        assertEquals("", env.getProperty("mercadopago.access-token"));
        assertEquals("http://localhost:4200/pago/resultado", env.getProperty("mercadopago.success-url"));
        assertEquals("http://localhost:4200/pago/resultado", env.getProperty("paypal.cancel-url"));
        assertEquals("https://api-m.sandbox.paypal.com", env.getProperty("paypal.base-url"));
        assertEquals("http://localhost:8080", env.getProperty("pagos.backend-url"));
    }

    @Test
    void local_appPublicUrlDefineTodasLasUrlsDeRetorno() throws IOException {
        StandardEnvironment env = entorno("application.properties", Map.of("APP_PUBLIC_URL", "https://mi-app.vercel.app"));

        assertEquals("https://mi-app.vercel.app/pago/resultado", env.getProperty("mercadopago.success-url"));
        assertEquals("https://mi-app.vercel.app/pago/resultado", env.getProperty("mercadopago.failure-url"));
        assertEquals("https://mi-app.vercel.app/pago/resultado", env.getProperty("mercadopago.pending-url"));
        assertEquals("https://mi-app.vercel.app/pago/resultado", env.getProperty("paypal.success-url"));
    }

    @Test
    void urlEspecificaTienePrioridadSobreLaDerivada() throws IOException {
        StandardEnvironment env = entorno("application.properties", Map.of(
            "APP_PUBLIC_URL", "https://mi-app.vercel.app",
            "MP_FAILURE_URL", "https://mi-app.vercel.app/pago/fallo"));

        assertEquals("https://mi-app.vercel.app/pago/fallo", env.getProperty("mercadopago.failure-url"));
        assertEquals("https://mi-app.vercel.app/pago/resultado", env.getProperty("mercadopago.success-url"));
    }

    @Test
    void produccion_sinVariables_nuncaApuntaALocalhostYUsaElBackendDeRailway() throws IOException {
        StandardEnvironment env = entorno("application-production.properties", Map.of());

        assertEquals("", env.getProperty("mercadopago.access-token"));
        assertEquals("", env.getProperty("paypal.client-secret"));
        assertEquals("/pago/resultado", env.getProperty("mercadopago.success-url"));
        assertEquals("https://agenteatencionback-production.up.railway.app", env.getProperty("pagos.backend-url"));
    }

    @Test
    void produccion_paypalLiveSeActivaConPaypalBaseUrl() throws IOException {
        StandardEnvironment env = entorno("application-production.properties",
            Map.of("PAYPAL_BASE_URL", "https://api-m.paypal.com"));

        assertEquals("https://api-m.paypal.com", env.getProperty("paypal.base-url"));
    }

    @Test
    void produccion_conVariables_construyeLasUrlsLive() throws IOException {
        StandardEnvironment env = entorno("application-production.properties", Map.of(
            "MP_ENABLED", "true",
            "APP_PUBLIC_URL", "https://barberia.example.com",
            "MP_ACCESS_TOKEN", "token-x"));

        assertEquals("true", env.getProperty("mercadopago.enabled"));
        assertEquals("token-x", env.getProperty("mercadopago.access-token"));
        assertEquals("https://barberia.example.com/pago/resultado", env.getProperty("paypal.success-url"));
    }
}
