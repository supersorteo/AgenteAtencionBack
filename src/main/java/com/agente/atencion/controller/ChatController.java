package com.agente.atencion.controller;

import com.agente.atencion.dto.ChatRequest;
import com.agente.atencion.dto.ChatResponse;
import com.agente.atencion.service.AgenteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired private AgenteService agenteService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            String respuesta = agenteService.chat(request.tenantId(), request.sessionId(), request.mensaje());
            return ResponseEntity.ok(new ChatResponse(respuesta));
        } catch (Exception e) {
            log.error("No se pudo completar la solicitud al agente", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse("El asistente no está disponible en este momento. Intentá de nuevo más tarde."));
        }
    }
}
