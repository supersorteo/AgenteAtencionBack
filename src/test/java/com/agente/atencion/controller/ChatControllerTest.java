package com.agente.atencion.controller;

import com.agente.atencion.dto.ChatRequest;
import com.agente.atencion.service.AgenteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {
    @Mock AgenteService agenteService;
    @InjectMocks ChatController controller;

    @Test
    void respuestaExitosaMantieneElContrato() {
        when(agenteService.chat("barberia-demo", "test", "hola")).thenReturn("Hola");

        var response = controller.chat(new ChatRequest("barberia-demo", "test", "hola"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Hola", response.getBody().respuesta());
    }

    @Test
    void falloDelProveedorRetorna503SinExponerDetallesInternos() {
        when(agenteService.chat("barberia-demo", "test", "hola"))
            .thenThrow(new RuntimeException("Provider model access denied: internal-detail"));

        var response = controller.chat(new ChatRequest("barberia-demo", "test", "hola"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().respuesta().contains("no está disponible"));
        assertFalse(response.getBody().respuesta().contains("internal-detail"));
    }
}
