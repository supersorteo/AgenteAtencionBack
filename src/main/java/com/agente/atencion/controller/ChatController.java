package com.agente.atencion.controller;

import com.agente.atencion.dto.ChatRequest;
import com.agente.atencion.dto.ChatResponse;
import com.agente.atencion.service.AgenteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired private AgenteService agenteService;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            String respuesta = agenteService.chat(request.tenantId(), request.sessionId(), request.mensaje());
            return ResponseEntity.ok(new ChatResponse(respuesta));
        } catch (Exception e) {
            return ResponseEntity.ok(new ChatResponse("Lo siento, ocurrió un error. Por favor intentá de nuevo."));
        }
    }
}
