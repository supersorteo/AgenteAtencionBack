package com.agente.atencion.security;

import com.agente.atencion.repository.TenantRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantRepository tenantRepo;

    public JwtFilter(JwtUtil jwtUtil, TenantRepository tenantRepo) {
        this.jwtUtil = jwtUtil;
        this.tenantRepo = tenantRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.valido(token)) {
                Claims claims = jwtUtil.parsear(token);
                String rol = claims.get("rol", String.class);
                String tenantId = claims.get("tenantId", String.class);
                Long barberoId = claims.get("barberoId", Long.class);

                // SUPER_ADMIN (tenantId="system") no está asociado a ningún tenant
                if (!"system".equals(tenantId)) {
                    boolean activo = tenantRepo.findById(tenantId)
                        .map(t -> t.isActivo()).orElse(false);
                    if (!activo) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"error\":\"Barbería desactivada\",\"code\":\"TENANT_INACTIVE\"}");
                        return;
                    }
                }

                var principal = new UsuarioAutenticado(claims.getSubject(), rol, tenantId, barberoId);
                var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }
}
