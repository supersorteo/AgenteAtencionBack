package com.agente.atencion.service;

import com.agente.atencion.repository.PagoRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoLimpiezaJobTest {

    @Test
    void borraLasPendientesDeMasDe30Dias() {
        PagoRepository pagos = mock(PagoRepository.class);
        Clock reloj = Clock.fixed(Instant.parse("2026-11-12T04:30:00Z"), ZoneOffset.UTC);
        LocalDateTime limite = LocalDateTime.of(2026, 10, 13, 4, 30);
        when(pagos.eliminarPendientesAnterioresA(limite)).thenReturn(7);

        int borradas = new PagoLimpiezaJob(pagos, reloj).limpiar();

        assertEquals(7, borradas);
        verify(pagos).eliminarPendientesAnterioresA(limite);
    }
}
