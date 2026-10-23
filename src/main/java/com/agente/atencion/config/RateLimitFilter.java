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

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(20)
                .refillGreedy(20, Duration.ofMinutes(1))
                .build())
            .build();
    }

    private static final int CLEANUP_THRESHOLD = 500;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        boolean isPublicPost = "POST".equals(req.getMethod()) &&
            (path.contains("/turnos") || path.contains("/chat") || path.contains("/auth/login"));
        if (isPublicPost) {
            if (buckets.size() > CLEANUP_THRESHOLD) {
                buckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() == 20);
            }
            String ip = req.getRemoteAddr();
            Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
            if (!bucket.tryConsume(1)) {
                res.setStatus(429);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Demasiadas solicitudes. Intenta en un minuto.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
