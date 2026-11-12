package com.agente.atencion.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private final long[] ahora = {0L};
    private final LoginAttemptService servicio = new LoginAttemptService((Ticker) () -> ahora[0]);

    @Test
    void tras5FallosDesdeLaMismaIpElUsuarioQuedaBloqueadoParaEsaIp() {
        for (int i = 0; i < 4; i++) servicio.fallo("1.1.1.1", "Ana");
        assertFalse(servicio.bloqueado("1.1.1.1", "ana"));

        servicio.fallo("1.1.1.1", "ANA");

        assertTrue(servicio.bloqueado("1.1.1.1", "ana"));
    }

    @Test
    void otraIpNoQuedaBloqueadaPorLosFallosDeUnAtacante() {
        for (int i = 0; i < 5; i++) servicio.fallo("6.6.6.6", "ana");

        assertTrue(servicio.bloqueado("6.6.6.6", "ana"));
        assertFalse(servicio.bloqueado("2.2.2.2", "ana"));
    }

    @Test
    void unAtaqueDistribuidoContraUnaCuentaTerminaBloqueandola() {
        for (int i = 0; i < 25; i++) servicio.fallo("10.0.0." + i, "ana");

        assertTrue(servicio.bloqueado("9.9.9.9", "ana"));
        assertFalse(servicio.bloqueado("9.9.9.9", "luis"));
    }

    @Test
    void unInicioDeSesionCorrectoLimpiaLosFallosDeEsaIp() {
        for (int i = 0; i < 4; i++) servicio.fallo("1.1.1.1", "ana");
        servicio.exito("1.1.1.1", "ana");
        servicio.fallo("1.1.1.1", "ana");

        assertFalse(servicio.bloqueado("1.1.1.1", "ana"));
    }

    @Test
    void elBloqueoSeLevantaALos15Minutos() {
        for (int i = 0; i < 5; i++) servicio.fallo("1.1.1.1", "ana");
        assertTrue(servicio.bloqueado("1.1.1.1", "ana"));

        ahora[0] += TimeUnit.MINUTES.toNanos(16);

        assertFalse(servicio.bloqueado("1.1.1.1", "ana"));
    }
}
