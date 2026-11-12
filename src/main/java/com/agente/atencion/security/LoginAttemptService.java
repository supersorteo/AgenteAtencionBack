package com.agente.atencion.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginAttemptService {

    static final int MAX_POR_IP_Y_USUARIO = 5;
    // Tope más alto por usuario solo: frena ataques distribuidos sin permitir que un tercero bloquee la cuenta con 5 intentos
    static final int MAX_POR_USUARIO = 25;

    private final Cache<String, AtomicInteger> fallos;

    public LoginAttemptService() {
        this(Ticker.systemTicker());
    }

    LoginAttemptService(Ticker reloj) {
        this.fallos = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .ticker(reloj)
            .build();
    }

    public boolean bloqueado(String ip, String usuario) {
        return cuenta(clavePar(ip, usuario)) >= MAX_POR_IP_Y_USUARIO
            || cuenta(claveUsuario(usuario)) >= MAX_POR_USUARIO;
    }

    public void fallo(String ip, String usuario) {
        fallos.get(clavePar(ip, usuario), k -> new AtomicInteger()).incrementAndGet();
        fallos.get(claveUsuario(usuario), k -> new AtomicInteger()).incrementAndGet();
    }

    public void exito(String ip, String usuario) {
        fallos.invalidate(clavePar(ip, usuario));
    }

    private int cuenta(String clave) {
        AtomicInteger n = fallos.getIfPresent(clave);
        return n == null ? 0 : n.get();
    }

    private static String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    private static String clavePar(String ip, String usuario) {
        return "p|" + ip + "|" + normalizar(usuario);
    }

    private static String claveUsuario(String usuario) {
        return "u|" + normalizar(usuario);
    }
}
