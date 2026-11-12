package com.agente.atencion.controller;

import com.agente.atencion.dto.ChatRequest;
import com.agente.atencion.service.AgenteService;
import com.agente.atencion.service.ChatGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerRechazoTest {
    @Mock AgenteService agenteService;
    @InjectMocks ChatController controller;

    @Test
    void unRechazoDelGuardiaLlegaAlClienteConSuEstadoYMensajeAmable() {
        when(agenteService.chat("t", "s", "hola", "127.0.0.1"))
            .thenThrow(new ChatGuard.Rechazo(HttpStatus.TOO_MANY_REQUESTS, "Límite de hoy alcanzado"));

        var r = controller.chat(new ChatRequest("t", "s", "hola"), new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, r.getStatusCode());
        assertEquals("Límite de hoy alcanzado", r.getBody().respuesta());
    }
}
