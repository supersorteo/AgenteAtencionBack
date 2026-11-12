package com.agente.atencion.controller;

import com.agente.atencion.service.DemoCredencialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoCredencialService credenciales;

    public DemoController(DemoCredencialService credenciales) {
        this.credenciales = credenciales;
    }

    // Público a propósito: son las credenciales de la barbería demo, que el login muestra precargadas
    @GetMapping("/credenciales")
    public ResponseEntity<?> credenciales() {
        return credenciales.obtener()
            .<ResponseEntity<?>>map(c -> ResponseEntity.ok(Map.of("username", c.getUsername(), "password", c.getPassword())))
            .orElse(ResponseEntity.notFound().build());
    }
}
