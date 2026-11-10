package com.agente.atencion.security;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.service.TenantConfigService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    private static final String TENANT = "barberia-x";

    @Mock TenantConfigService tenants;
    @Mock AuthCacheService authCache;

    private final JwtUtil jwt = new JwtUtil("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres");
    private JwtFilter filtro;

    @BeforeEach
    void setUp() {
        filtro = new JwtFilter(jwt, tenants, authCache);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Tenant tenant(boolean activo, LocalDate vencimiento) {
        Tenant t = new Tenant();
        t.setId(TENANT);
        t.setActivo(activo);
        t.setFechaVencimientoSuscripcion(vencimiento);
        return t;
    }

    private record Resultado(MockHttpServletResponse respuesta, MockFilterChain cadena) {
        boolean continuo() { return cadena.getRequest() != null; }
    }

    private Resultado ejecutar(String token) throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (token != null) req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(req, res, cadena);
        return new Resultado(res, cadena);
    }

    private String tokenAdmin() {
        return jwt.generar("ana", "ADMIN", TENANT, null);
    }

    @Test
    void usuarioActivoYNegocioActivoPasaYQuedaAutenticado() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, null));
        when(authCache.usuario("ana")).thenReturn(Optional.of(new AuthCacheService.UsuarioAuth(true, null)));

        Resultado r = ejecutar(tokenAdmin());

        assertTrue(r.continuo());
        UsuarioAutenticado principal = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("ana", principal.username());
        assertEquals(TENANT, principal.tenantId());
    }

    @Test
    void sinTokenSigueSinAutenticarYNoConsultaNada() throws Exception {
        Resultado r = ejecutar(null);

        assertTrue(r.continuo());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tenants, authCache);
    }

    @Test
    void tokenFalsoSigueSinAutenticarYNoConsultaNada() throws Exception {
        Resultado r = ejecutar("esto.no.es.un.token");

        assertTrue(r.continuo());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(tenants, authCache);
    }

    @Test
    void negocioInexistenteSeRechazaConDesactivado() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(null);

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(403, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("TENANT_INACTIVE"));
        assertTrue(!r.continuo());
        verify(authCache, never()).usuario(anyString());
    }

    @Test
    void negocioDesactivadoSeRechaza() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(false, null));

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(403, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("TENANT_INACTIVE"));
    }

    @Test
    void suscripcionVencidaMasAllaDeLaGraciaSeRechazaConSuCodigo() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, LocalDate.now().minusDays(30)));

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(403, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("SUBSCRIPTION_EXPIRED"));
        assertTrue(!r.continuo());
    }

    @Test
    void usuarioInexistenteSeRechazaConUnauthorized() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, null));
        when(authCache.usuario("ana")).thenReturn(Optional.empty());

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(401, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("USER_INVALID"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void usuarioDesactivadoSeRechaza() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, null));
        when(authCache.usuario("ana")).thenReturn(Optional.of(new AuthCacheService.UsuarioAuth(false, null)));

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(401, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("USER_INVALID"));
    }

    @Test
    void tokenEmitidoAntesDelCambioDeContrasenaSeRevoca() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, null));
        LocalDateTime cambioPosterior = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        when(authCache.usuario("ana")).thenReturn(Optional.of(new AuthCacheService.UsuarioAuth(true, cambioPosterior)));

        Resultado r = ejecutar(tokenAdmin());

        assertEquals(401, r.respuesta().getStatus());
        assertTrue(r.respuesta().getContentAsString().contains("TOKEN_REVOKED"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void cambioDeContrasenaAnteriorAlTokenNoLoRevoca() throws Exception {
        when(tenants.getTenant(TENANT)).thenReturn(tenant(true, null));
        LocalDateTime cambioPrevio = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        when(authCache.usuario("ana")).thenReturn(Optional.of(new AuthCacheService.UsuarioAuth(true, cambioPrevio)));

        Resultado r = ejecutar(tokenAdmin());

        assertTrue(r.continuo());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void elSuperadminNoDependeDeNingunNegocio() throws Exception {
        when(authCache.usuario("root")).thenReturn(Optional.of(new AuthCacheService.UsuarioAuth(true, null)));

        Resultado r = ejecutar(jwt.generar("root", "SUPER_ADMIN", "system", null));

        assertTrue(r.continuo());
        verifyNoInteractions(tenants);
    }
}
