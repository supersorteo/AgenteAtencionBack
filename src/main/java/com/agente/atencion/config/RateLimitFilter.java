package com.agente.atencion.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    // Strict: login y registro — 5 intentos/min para mitigar brute force
    private final Map<String, Bucket> strictBuckets = new ConcurrentHashMap<>();
    // Normal: chat y turnos — 20 requests/min
    private final Map<String, Bucket> normalBuckets = new ConcurrentHashMap<>();

    private static Bucket newStrictBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
            .build();
    }

    private static Bucket newNormalBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(20).refillGreedy(20, Duration.ofMinutes(1)).build())
            .build();
    }

    private static final int CLEANUP_THRESHOLD = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        String method = req.getMethod();

        boolean isStrictPost = "POST".equals(method) &&
            (path.startsWith("/api/auth/login") || path.startsWith("/api/registro"));
        boolean isNormalPost = "POST".equals(method) &&
            (path.contains("/turnos") || path.contains("/chat") || path.startsWith("/api/pagos/mercadopago/")
                || path.startsWith("/api/pagos/paypal/"));

        if (isStrictPost || isNormalPost) {
            String ip = req.getRemoteAddr();
            if (isStrictPost) {
                if (strictBuckets.size() > CLEANUP_THRESHOLD)
                    strictBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() == 5);
                if (!strictBuckets.computeIfAbsent(ip, k -> newStrictBucket()).tryConsume(1)) {
                    res.setStatus(429);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Demasiados intentos. Esperá un minuto.\"}");
                    return;
                }
            } else {
                if (normalBuckets.size() > CLEANUP_THRESHOLD)
                    normalBuckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() == 20);
                if (!normalBuckets.computeIfAbsent(ip, k -> newNormalBucket()).tryConsume(1)) {
                    res.setStatus(429);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Demasiadas solicitudes. Intentá en un minuto.\"}");
                    return;
                }
            }
        }
        chain.doFilter(req, res);
    }
}
