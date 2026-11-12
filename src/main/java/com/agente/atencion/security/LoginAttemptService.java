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
    // Tope por usuario entre todas las IPs: frena ataques distribuidos
    static final int MAX_POR_USUARIO = 25;

    private final Cache<String, AtomicInteger> intentos;
    // IPs desde las que ese usuario ya inició sesión bien: el tope por usuario no las bloquea, así un atacante no deja afuera al dueño de la cuenta
    private final Cache<String, Boolean> conocidas;

    public LoginAttemptService() {
        this(Ticker.systemTicker());
    }

    LoginAttemptService(Ticker reloj) {
        this.intentos = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofMinutes(15)).ticker(reloj).build();
        this.conocidas = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(Duration.ofDays(30)).ticker(reloj).build();
    }

    // Reserva el intento antes de comprobar la clave, de forma atómica: varias peticiones paralelas no pueden colarse entre la lectura y la escritura
    public boolean permitir(String ip, String usuario) {
        int delPar = intentos.get(clavePar(ip, usuario), k -> new AtomicInteger()).incrementAndGet();
        int delUsuario = intentos.get(claveUsuario(usuario), k -> new AtomicInteger()).incrementAndGet();
        if (delPar > MAX_POR_IP_Y_USUARIO) return false;
        return delUsuario <= MAX_POR_USUARIO || conocidas.getIfPresent(clavePar(ip, usuario)) != null;
    }

    public void exito(String ip, String usuario) {
        intentos.invalidate(clavePar(ip, usuario));
        AtomicInteger delUsuario = intentos.getIfPresent(claveUsuario(usuario));
        if (delUsuario != null) delUsuario.updateAndGet(n -> Math.max(0, n - 1));
        conocidas.put(clavePar(ip, usuario), Boolean.TRUE);
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
