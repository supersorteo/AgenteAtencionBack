package com.agente.atencion.security;

import com.agente.atencion.service.TenantConfigService;
import com.agente.atencion.service.SuscripcionService;
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
    private final TenantConfigService tenantConfigService;
    private final AuthCacheService authCache;

    public JwtFilter(JwtUtil jwtUtil, TenantConfigService tenantConfigService, AuthCacheService authCache) {
        this.jwtUtil = jwtUtil;
        this.tenantConfigService = tenantConfigService;
        this.authCache = authCache;
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
                    var tenant = tenantConfigService.getTenant(tenantId);
                    var estado = tenant == null
                        ? SuscripcionService.EstadoAcceso.DESACTIVADO
                        : SuscripcionService.estado(tenant);
                    if (estado != SuscripcionService.EstadoAcceso.ACTIVO) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json");
                        res.getWriter().write(estado == SuscripcionService.EstadoAcceso.VENCIDO
                            ? "{\"error\":\"La suscripción de tu barbería venció. Renová tu plan para continuar.\",\"code\":\"SUBSCRIPTION_EXPIRED\"}"
                            : "{\"error\":\"Barbería desactivada\",\"code\":\"TENANT_INACTIVE\"}");
                        return;
                    }
                }

                // Fail-closed: rechazar si el usuario no existe o está inactivo
                var usuarioOpt = authCache.usuario(claims.getSubject());
                if (usuarioOpt.isEmpty() || !usuarioOpt.get().activo()) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Usuario no válido.\",\"code\":\"USER_INVALID\"}");
                    return;
                }

                // Invalidar tokens emitidos antes del último cambio de contraseña
                LocalDateTime issuedAt = claims.getIssuedAt()
                    .toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
                if (usuarioOpt.get().passwordChangedAt() != null
                        && issuedAt.isBefore(usuarioOpt.get().passwordChangedAt())) {
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
        if (SecurityContextHolder.getContext().getAuthentication() == null && catalogoPublicoDeNegocioInactivo(req)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Barbería no disponible\",\"code\":\"TENANT_INACTIVE\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static final java.util.Set<String> RECURSOS_PUBLICOS = java.util.Set.of(
        "servicios", "barberos", "galeria", "disponibilidad", "config", "categorias");

    // GET anónimo de /api/v1/<recurso>/<tenantId>: un negocio desactivado o vencido no expone su catálogo
    private boolean catalogoPublicoDeNegocioInactivo(HttpServletRequest req) {
        if (!"GET".equals(req.getMethod())) return false;
        // Misma ruta decodificada y normalizada que usa Spring MVC: con la URI cruda, "/servicios/%74%31" esquivaría el chequeo
        String[] partes = org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(req).split("/");
        // ["", "api", "v1", recurso, tenantId, ...]
        if (partes.length < 5 || !"api".equals(partes[1]) || !"v1".equals(partes[2]) || !RECURSOS_PUBLICOS.contains(partes[3])) {
            return false;
        }
        var tenant = tenantConfigService.getTenant(partes[4]);
        return tenant != null && SuscripcionService.estado(tenant) != SuscripcionService.EstadoAcceso.ACTIVO;
    }
}
