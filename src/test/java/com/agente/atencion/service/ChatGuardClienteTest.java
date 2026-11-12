package com.agente.atencion.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatGuardClienteTest {

    private ChatGuard guardia(int porNegocio, int porCliente) {
        return new ChatGuard(true, 5, porNegocio, porCliente, 3, 30, 5_000, System::currentTimeMillis);
    }

    @Test
    void unSoloClienteNoPuedeAgotarElCupoDelNegocio() {
        ChatGuard g = guardia(100, 2);
        g.ejecutar("a", "ip-abusiva", () -> "1");
        g.ejecutar("a", "ip-abusiva", () -> "2");

        ChatGuard.Rechazo r = assertThrows(ChatGuard.Rechazo.class, () -> g.ejecutar("a", "ip-abusiva", () -> "3"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, r.status());
        assertEquals("otro cliente igual atendido", g.ejecutar("a", "ip-normal", () -> "otro cliente igual atendido"));
    }

    @Test
    void losRechazadosPorSuLimitePersonalNoGastanElCupoDelNegocio() {
        ChatGuard g = guardia(3, 1);
        g.ejecutar("a", "x", () -> "ok");
        for (int i = 0; i < 20; i++) {
            assertThrows(ChatGuard.Rechazo.class, () -> g.ejecutar("a", "x", () -> "no"));
        }

        assertEquals("b", g.ejecutar("a", "y", () -> "b"));
        assertEquals("c", g.ejecutar("a", "z", () -> "c"));
    }

    @Test
    void elCupoDelNegocioSigueAplicandoEntreClientesDistintos() {
        ChatGuard g = guardia(2, 10);
        g.ejecutar("a", "x", () -> "1");
        g.ejecutar("a", "y", () -> "2");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            assertThrows(ChatGuard.Rechazo.class, () -> g.ejecutar("a", "z", () -> "3")).status());
    }
}
