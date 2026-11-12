package com.agente.atencion.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Component
public class ChatGuard {

    private static final Logger log = LoggerFactory.getLogger(ChatGuard.class);

    public static class Rechazo extends RuntimeException {
        private final HttpStatus status;

        public Rechazo(HttpStatus status, String mensaje) {
            super(mensaje);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final boolean habilitado;
    private final int maxMensajesDia;
    private final int maxPorCliente;
    private final int fallosParaAbrir;
    private final long pausaCircuitoMs;
    private final LongSupplier reloj;
    private final long timeoutMs;
    private final ExecutorService ejecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore concurrentes;
    private final Cache<String, AtomicInteger> mensajesPorDia =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofHours(25)).build();
    private final AtomicInteger fallosSeguidos = new AtomicInteger();
    private volatile long circuitoAbiertoHasta;

    @Autowired
    public ChatGuard(@Value("${chat.habilitado:true}") boolean habilitado,
                     @Value("${chat.max-concurrentes:20}") int maxConcurrentes,
                     @Value("${chat.max-mensajes-dia:500}") int maxMensajesDia,
                     @Value("${chat.max-mensajes-dia-por-cliente:60}") int maxPorCliente,
                     @Value("${chat.fallos-para-abrir:5}") int fallosParaAbrir,
                     @Value("${chat.pausa-circuito-segundos:30}") int pausaSegundos,
                     @Value("${chat.timeout-segundos:35}") int timeoutSegundos) {
        this(habilitado, maxConcurrentes, maxMensajesDia, maxPorCliente, fallosParaAbrir, pausaSegundos, timeoutSegundos * 1000L, System::currentTimeMillis);
    }

    ChatGuard(boolean habilitado, int maxConcurrentes, int maxMensajesDia, int maxPorCliente, int fallosParaAbrir,
              int pausaSegundos, long timeoutMs, LongSupplier reloj) {
        this.timeoutMs = timeoutMs;
        this.habilitado = habilitado;
        this.maxMensajesDia = maxMensajesDia;
        this.maxPorCliente = maxPorCliente;
        this.fallosParaAbrir = fallosParaAbrir;
        this.pausaCircuitoMs = pausaSegundos * 1000L;
        this.reloj = reloj;
        this.concurrentes = new Semaphore(maxConcurrentes);
    }

    public <T> T ejecutar(String tenantId, String cliente, Supplier<T> llamada) {
        if (!habilitado) {
            throw new Rechazo(HttpStatus.SERVICE_UNAVAILABLE, "El asistente está en mantenimiento. Escribinos por WhatsApp y te ayudamos.");
        }
        if (reloj.getAsLong() < circuitoAbiertoHasta) {
            throw new Rechazo(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no está disponible en este momento. Intentá de nuevo en un rato.");
        }
        String dia = LocalDate.now(ZoneOffset.UTC).toString();
        AtomicInteger delCliente = mensajesPorDia.get(tenantId + "_" + dia + "_c_" + cliente, k -> new AtomicInteger());
        if (delCliente.incrementAndGet() > maxPorCliente) {
            throw new Rechazo(HttpStatus.TOO_MANY_REQUESTS, "Llegaste al límite de consultas de hoy. Escribinos por WhatsApp o probá mañana.");
        }
        AtomicInteger delNegocio = mensajesPorDia.get(tenantId + "_" + dia, k -> new AtomicInteger());
        if (delNegocio.incrementAndGet() > maxMensajesDia) {
            delCliente.decrementAndGet();
            throw new Rechazo(HttpStatus.TOO_MANY_REQUESTS, "El asistente llegó al límite de consultas de hoy. Escribinos por WhatsApp o probá mañana.");
        }
        if (!concurrentes.tryAcquire()) {
            delCliente.decrementAndGet();
            delNegocio.decrementAndGet();
            throw new Rechazo(HttpStatus.SERVICE_UNAVAILABLE, "El asistente tiene mucha demanda ahora. Probá de nuevo en unos segundos.");
        }
        Future<T> tarea;
        try {
            tarea = ejecutor.submit(() -> {
                try {
                    return llamada.get();
                } finally {
                    concurrentes.release();
                }
            });
        } catch (RuntimeException e) {
            concurrentes.release();
            throw e;
        }
        try {
            T resultado = tarea.get(timeoutMs, TimeUnit.MILLISECONDS);
            fallosSeguidos.set(0);
            return resultado;
        } catch (TimeoutException e) {
            tarea.cancel(true);
            registrarFallo();
            throw new Rechazo(HttpStatus.SERVICE_UNAVAILABLE, "El asistente tardó demasiado en responder. Probá de nuevo en un momento.");
        } catch (ExecutionException e) {
            registrarFallo();
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            tarea.cancel(true);
            throw new Rechazo(HttpStatus.SERVICE_UNAVAILABLE, "El asistente no está disponible en este momento.");
        }
    }

    private void registrarFallo() {
        if (fallosSeguidos.incrementAndGet() >= fallosParaAbrir) {
            circuitoAbiertoHasta = reloj.getAsLong() + pausaCircuitoMs;
            fallosSeguidos.set(0);
            log.warn("[ChatGuard] circuito abierto {} s tras {} fallos seguidos del proveedor", pausaCircuitoMs / 1000, fallosParaAbrir);
        }
    }

    @PreDestroy
    void cerrar() {
        ejecutor.shutdownNow();
    }
}
