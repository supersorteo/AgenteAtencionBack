package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.AuthCacheService;
import com.agente.atencion.security.JwtUtil;
import com.agente.atencion.security.LoginAttemptService;
import com.agente.atencion.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProteccionDemoTest {

    private static final String DEMO = "barberia-demo";

    private UsuarioRepository repo;
    private PasswordEncoder encoder;
    private MockMvc authMvc;
    private MockMvc usuariosMvc;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        AuthCacheService cache = mock(AuthCacheService.class);
        authMvc = MockMvcBuilders.standaloneSetup(new AuthController(repo, mock(TenantRepository.class),
            new JwtUtil("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres"), encoder, cache, new LoginAttemptService())).build();
        usuariosMvc = MockMvcBuilders.standaloneSetup(new UsuarioAdminController(repo, mock(BarberoRepository.class), encoder, cache)).build();
    }

    private static Authentication admin(String tenant) {
        return new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("admin", "ADMIN", tenant, null), null, List.of());
    }

    private static Usuario usuario(String tenant, String rol) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setUsername("admin");
        u.setTenantId(tenant);
        u.setRol(rol);
        u.setPassword("hash");
        return u;
    }

    @Test
    void elAdminDeLaDemoNoPuedeCambiarSuPropiaContrasena() throws Exception {
        authMvc.perform(patch("/api/auth/cambiar-password").principal(admin(DEMO)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"admin123\",\"nueva\":\"OtraClave123\"}"))
            .andExpect(status().isForbidden());

        verify(repo, never()).save(any());
    }

    @Test
    void elAdminDeUnNegocioNormalSigueCambiandoSuContrasena() throws Exception {
        Usuario u = usuario("otro", "ADMIN");
        when(repo.findByUsernameAndActivoTrue("admin")).thenReturn(Optional.of(u));
        when(encoder.matches("ClaveActual1", "hash")).thenReturn(true);
        when(encoder.encode("ClaveNueva123")).thenReturn("hash-nuevo");

        authMvc.perform(patch("/api/auth/cambiar-password").principal(admin("otro")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"actual\":\"ClaveActual1\",\"nueva\":\"ClaveNueva123\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void enLaDemoNoSePuedeReiniciarLaContrasenaDelAdministrador() throws Exception {
        when(repo.findById(1L)).thenReturn(Optional.of(usuario(DEMO, "ADMIN")));

        usuariosMvc.perform(patch("/api/v1/admin/" + DEMO + "/usuarios/1/password").principal(admin(DEMO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"OtraClave123\"}"))
            .andExpect(status().isForbidden());

        verify(repo, never()).save(any());
    }

    @Test
    void enLaDemoSiSePuedeReiniciarLaContrasenaDeUnBarbero() throws Exception {
        Usuario barbero = usuario(DEMO, "BARBERO");
        barbero.setUsername("juan");
        when(repo.findById(1L)).thenReturn(Optional.of(barbero));
        when(encoder.encode("OtraClave123")).thenReturn("hash-nuevo");

        usuariosMvc.perform(patch("/api/v1/admin/" + DEMO + "/usuarios/1/password").principal(admin(DEMO))
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"OtraClave123\"}"))
            .andExpect(status().isOk());
    }
}
