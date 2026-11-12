package com.agente.atencion.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatGuardTest {

    private final long[] ahora = {0L};

    private ChatGuard guardia(boolean habilitado, int concurrentes, int porDia, int fallos) {
        return new ChatGuard(habilitado, concurrentes, porDia, porDia, fallos, 30, 5_000, () -> ahora[0]);
    }

    private static ChatGuard.Rechazo rechazo(Runnable r) {
        return assertThrows(ChatGuard.Rechazo.class, r::run);
    }

    @Test
    void devuelveLaRespuestaDelProveedor() {
        assertEquals("hola", guardia(true, 5, 10, 3).ejecutar("a", "ip", () -> "hola"));
    }

    @Test
    void apagadoGlobalRechazaSinLlamarAlProveedor() {
        ChatGuard g = guardia(false, 5, 10, 3);

        ChatGuard.Rechazo r = rechazo(() -> g.ejecutar("a", "ip", () -> { throw new AssertionError("no debe llamar"); }));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.status());
    }

    @Test
    void superadoElPresupuestoDiarioDeUnNegocioResponde429SoloParaEse() {
        ChatGuard g = guardia(true, 5, 2, 3);
        g.ejecutar("a", "ip", () -> "1");
        g.ejecutar("a", "ip", () -> "2");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rechazo(() -> g.ejecutar("a", "ip", () -> "3")).status());
        assertEquals("ok", g.ejecutar("b", "ip", () -> "ok"));
    }

    @Test
    void sinCupoDeConcurrenciaRechazaAlInstante() throws Exception {
        ChatGuard g = guardia(true, 1, 100, 3);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> lenta = pool.submit(() -> g.ejecutar("a", "ip", () -> {
                dentro.countDown();
                try { soltar.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignorada) { Thread.currentThread().interrupt(); }
                return "lenta";
            }));
            dentro.await(5, TimeUnit.SECONDS);

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rechazo(() -> g.ejecutar("b", "ip", () -> "x")).status());

            soltar.countDown();
            assertEquals("lenta", lenta.get(5, TimeUnit.SECONDS));
            assertEquals("libre", g.ejecutar("b", "ip", () -> "libre"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tras_variosFallosSeguidosElCircuitoSeAbreYSeCierraConElTiempo() {
        ChatGuard g = guardia(true, 5, 100, 3);
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class, () -> g.ejecutar("a", "ip", () -> { throw new IllegalStateException("groq caído"); }));
        }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, rechazo(() -> g.ejecutar("a", "ip", () -> { throw new AssertionError("circuito abierto"); })).status());

        ahora[0] += 31_000;
        assertEquals("recuperado", g.ejecutar("a", "ip", () -> "recuperado"));
    }

    @Test
    void unaLlamadaColgadaSeCortaAlVencerElPlazoYLiberaElCupo() {
        ChatGuard g = new ChatGuard(true, 1, 100, 100, 5, 30, 200, () -> ahora[0]);
        long inicio = System.nanoTime();

        ChatGuard.Rechazo r = rechazo(() -> g.ejecutar("a", "ip", () -> {
            try { Thread.sleep(10_000); } catch (InterruptedException ignorada) { Thread.currentThread().interrupt(); }
            return "nunca";
        }));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.status());
        org.junit.jupiter.api.Assertions.assertTrue(System.nanoTime() - inicio < 3_000_000_000L, "debe cortar cerca del plazo");
        org.junit.jupiter.api.Assertions.assertEquals("libre", esperarCupo(g));
    }

    private static String esperarCupo(ChatGuard g) {
        for (int i = 0; i < 50; i++) {
            try { return g.ejecutar("a", "ip", () -> "libre"); }
            catch (ChatGuard.Rechazo e) { try { Thread.sleep(20); } catch (InterruptedException x) { Thread.currentThread().interrupt(); } }
        }
        return "sin cupo";
    }

    @Test
    void unExitoReiniciaLaCuentaDeFallos() {
        ChatGuard g = guardia(true, 5, 100, 3);
        for (int i = 0; i < 2; i++) {
            assertThrows(IllegalStateException.class, () -> g.ejecutar("a", "ip", () -> { throw new IllegalStateException("x"); }));
        }
        g.ejecutar("a", "ip", () -> "ok");
        assertThrows(IllegalStateException.class, () -> g.ejecutar("a", "ip", () -> { throw new IllegalStateException("x"); }));

        assertEquals("sigue abierto el servicio", g.ejecutar("a", "ip", () -> "sigue abierto el servicio"));
    }
}
