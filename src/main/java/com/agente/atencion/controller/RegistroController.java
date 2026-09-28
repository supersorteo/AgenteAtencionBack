package com.agente.atencion.controller;

import com.agente.atencion.dto.RegistroRequest;
import com.agente.atencion.dto.RegistroResponse;
import com.agente.atencion.service.RegistroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registro")
public class RegistroController {

    private final RegistroService registroService;

    public RegistroController(RegistroService registroService) {
        this.registroService = registroService;
    }

    @PostMapping
    public ResponseEntity<RegistroResponse> registrar(@RequestBody RegistroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registroService.registrar(req));
    }
}
