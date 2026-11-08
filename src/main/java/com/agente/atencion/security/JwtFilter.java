package com.agente.atencion.security;

import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantRepository tenantRepo;
    private final UsuarioRepository usuarioRepo;

    public JwtFilter(JwtUtil jwtUtil, TenantRepository tenantRepo, UsuarioRepository usuarioRepo) {
        this.jwtUtil = jwtUtil;
        this.tenantRepo = tenantRepo;
        this.usuarioRepo = usuarioRepo;
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

                // Fail-closed: rechazar si el usuario no existe o está inactivo
                var usuarioOpt = usuarioRepo.findByUsername(claims.getSubject());
                if (usuarioOpt.isEmpty() || !usuarioOpt.get().isActivo()) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Usuario no válido.\",\"code\":\"USER_INVALID\"}");
                    return;
                }

                // Invalidar tokens emitidos antes del último cambio de contraseña
                LocalDateTime issuedAt = claims.getIssuedAt()
                    .toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
                if (usuarioOpt.get().getPasswordChangedAt() != null
                        && issuedAt.isBefore(usuarioOpt.get().getPasswordChangedAt())) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Sesión expirada. Iniciá sesión nuevamente.\",\"code\":\"TOKEN_REVOKED\"}");
                    return;
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
