package com.agente.atencion.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    private final long[] ahora = {0L};
    private final LoginAttemptService servicio = new LoginAttemptService((Ticker) () -> ahora[0]);

    @Test
    void permiteCincoIntentosDesdeLaMismaIpYBloqueaElSexto() {
        for (int i = 0; i < 5; i++) assertTrue(servicio.permitir("1.1.1.1", i % 2 == 0 ? "Ana" : "ANA"));

        assertFalse(servicio.permitir("1.1.1.1", "ana"));
    }

    @Test
    void otraIpNoQuedaBloqueadaPorLosFallosDeUnAtacante() {
        for (int i = 0; i < 6; i++) servicio.permitir("6.6.6.6", "ana");

        assertFalse(servicio.permitir("6.6.6.6", "ana"));
        assertTrue(servicio.permitir("2.2.2.2", "ana"));
    }

    @Test
    void unAtaqueDistribuidoBloqueaIpsNuevasPeroNoAlDuenoConocidoDeLaCuenta() {
        assertTrue(servicio.permitir("5.5.5.5", "ana"));
        servicio.exito("5.5.5.5", "ana");

        for (int i = 0; i < 25; i++) servicio.permitir("10.0.0." + i, "ana");

        assertFalse(servicio.permitir("9.9.9.9", "ana"), "una IP nueva queda fuera mientras dura el ataque");
        assertTrue(servicio.permitir("5.5.5.5", "ana"), "el dueño desde su IP habitual sigue entrando");
        assertTrue(servicio.permitir("9.9.9.9", "luis"), "otros usuarios no se ven afectados");
    }

    @Test
    void unInicioDeSesionCorrectoLimpiaLosFallosDeEsaIp() {
        for (int i = 0; i < 4; i++) servicio.permitir("1.1.1.1", "ana");
        assertTrue(servicio.permitir("1.1.1.1", "ana"));
        servicio.exito("1.1.1.1", "ana");

        for (int i = 0; i < 5; i++) assertTrue(servicio.permitir("1.1.1.1", "ana"));
    }

    @Test
    void elBloqueoSeLevantaALos15Minutos() {
        for (int i = 0; i < 6; i++) servicio.permitir("1.1.1.1", "ana");
        assertFalse(servicio.permitir("1.1.1.1", "ana"));

        ahora[0] += TimeUnit.MINUTES.toNanos(16);

        assertTrue(servicio.permitir("1.1.1.1", "ana"));
    }

    @Test
    void cienPeticionesParalelasNoPasanDelLimite() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger permitidas = new AtomicInteger();
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                tareas.add(pool.submit(() -> {
                    try { salida.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (servicio.permitir("1.1.1.1", "ana")) permitidas.incrementAndGet();
                }));
            }
            salida.countDown();
            for (Future<?> t : tareas) t.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(5, permitidas.get());
    }
}
