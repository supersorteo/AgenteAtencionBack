package com.agente.atencion.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/diag")
public class DiagController {

    @Value("${spring.ai.openai.api-key:NOT_SET}") private String groqKey;
    @Value("${spring.ai.openai.base-url:NOT_SET}") private String baseUrl;
    @Value("${spring.ai.openai.chat.options.model:NOT_SET}") private String model;

    private final Environment env;
    public DiagController(Environment env) { this.env = env; }

    @GetMapping
    public Map<String, Object> diag() {
        String keyStatus = groqKey.equals("NOT_SET") ? "NOT_SET"
            : groqKey.isBlank() ? "EMPTY"
            : "SET";
        return Map.of(
            "profiles", Arrays.toString(env.getActiveProfiles()),
            "groqKeyStatus", keyStatus,
            "baseUrl", baseUrl,
            "model", model
        );
    }
}
