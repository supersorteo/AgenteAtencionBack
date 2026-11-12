package com.agente.atencion.security;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.service.TenantConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtFilterPublicoTest {

    private TenantConfigService tenants;
    private JwtFilter filtro;

    @BeforeEach
    void setUp() {
        tenants = mock(TenantConfigService.class);
        filtro = new JwtFilter(new JwtUtil("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres"), tenants, mock(AuthCacheService.class));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Tenant tenant(boolean activo, LocalDate vencimiento) {
        Tenant t = new Tenant();
        t.setId("t1");
        t.setActivo(activo);
        t.setFechaVencimientoSuscripcion(vencimiento);
        return t;
    }

    private MockHttpServletResponse pedir(String metodo, String uri, MockFilterChain cadena) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, uri);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, cadena);
        return res;
    }

    @Test
    void elCatalogoDeUnNegocioVencidoNoSeExponeAAnonimos() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(tenant(true, LocalDate.now().minusDays(30)));
        MockFilterChain cadena = new MockFilterChain();

        MockHttpServletResponse res = pedir("GET", "/api/v1/servicios/t1", cadena);

        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains("TENANT_INACTIVE"));
        assertNull(cadena.getRequest());
    }

    @Test
    void unTenantCodificadoConPorcentajeNoEsquivaElChequeo() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(tenant(true, LocalDate.now().minusDays(30)));

        assertEquals(403, pedir("GET", "/api/v1/servicios/%74%31", new MockFilterChain()).getStatus());
        assertEquals(403, pedir("GET", "/api/v1/servicios/t1;x=1", new MockFilterChain()).getStatus());
    }

    @Test
    void elCatalogoDeUnNegocioDesactivadoTampoco() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(tenant(false, null));

        assertEquals(403, pedir("GET", "/api/v1/barberos/t1", new MockFilterChain()).getStatus());
        assertEquals(403, pedir("GET", "/api/v1/disponibilidad/t1", new MockFilterChain()).getStatus());
    }

    @Test
    void unNegocioActivoSigueMostrandoSuCatalogo() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(tenant(true, LocalDate.now().plusDays(10)));
        MockFilterChain cadena = new MockFilterChain();

        assertEquals(200, pedir("GET", "/api/v1/servicios/t1", cadena).getStatus());
        assertNotNull(cadena.getRequest());
    }

    @Test
    void unNegocioInexistenteNoCambiaElComportamientoDeSiempre() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(null);
        MockFilterChain cadena = new MockFilterChain();

        pedir("GET", "/api/v1/servicios/t1", cadena);

        assertNotNull(cadena.getRequest());
    }

    @Test
    void soloAplicaALosGetDelCatalogoPublico() throws Exception {
        when(tenants.getTenant("t1")).thenReturn(tenant(false, null));

        MockFilterChain post = new MockFilterChain();
        pedir("POST", "/api/v1/turnos/t1", post);
        MockFilterChain otro = new MockFilterChain();
        pedir("GET", "/api/pagos/orden/ABC", otro);

        assertNotNull(post.getRequest());
        assertNotNull(otro.getRequest());
    }
}
