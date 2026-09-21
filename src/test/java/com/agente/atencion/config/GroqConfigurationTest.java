package com.agente.atencion.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroqConfigurationTest {
    private MockEnvironment configuration(boolean production) throws IOException {
        var environment = new MockEnvironment();
        var properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        if (production) {
            properties.putAll(PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-production.properties")));
        }
        properties.forEach((key, value) -> environment.setProperty(key.toString(), value.toString()));
        return environment;
    }

    @Test
    void ambosEntornosUsanElMismoModeloPorDefecto() throws IOException {
        for (boolean production : new boolean[]{false, true}) {
            assertEquals("qwen/qwen3.8-27b",
                configuration(production).getProperty("spring.ai.openai.chat.options.model"));
        }
    }

    @Test
    void ambosEntornosPermitenCambiarElModelo() throws IOException {
        for (boolean production : new boolean[]{false, true}) {
            var environment = configuration(production).withProperty("GROQ_MODEL", "modelo-configurado");
            assertEquals("modelo-configurado", environment.getProperty("spring.ai.openai.chat.options.model"));
        }
    }

    @Test
    void aceptaLaVariableExistenteDeRailwayYPriorizaElNombrePrincipal() throws IOException {
        for (boolean production : new boolean[]{false, true}) {
            var environment = configuration(production).withProperty("GROQ_KEY", "legacy-test-key");
            assertEquals("legacy-test-key", environment.getProperty("spring.ai.openai.api-key"));
            environment.setProperty("GROQ_API_KEY", "primary-test-key");
            assertEquals("primary-test-key", environment.getProperty("spring.ai.openai.api-key"));
        }
    }
}
