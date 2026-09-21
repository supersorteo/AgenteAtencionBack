package com.agente.atencion.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24h

    public JwtUtil(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generar(String username, String rol, String tenantId, Long barberoId) {
        var builder = Jwts.builder()
            .subject(username)
            .claim("rol", rol)
            .claim("tenantId", tenantId);
        if (barberoId != null) builder.claim("barberoId", barberoId);
        return builder
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
            .signWith(key)
            .compact();
    }

    public Claims parsear(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }

    public boolean valido(String token) {
        try { parsear(token); return true; } catch (Exception e) { return false; }
    }
}
