package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.AuthCacheService;
import com.agente.atencion.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioAdminControllerTest {

    private static final String TENANT = "barberia-a";

    @Mock UsuarioRepository usuarioRepo;
    @Mock BarberoRepository barberoRepo;
    @Mock PasswordEncoder encoder;
    @Mock AuthCacheService authCache;

    private MockMvc mvc;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioAdminController(usuarioRepo, barberoRepo, encoder, authCache)).build();
        admin = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("admin", "ADMIN", TENANT, null), null, List.of());
    }

    private Usuario barbero(String tenantId) {
        Usuario u = new Usuario();
        u.setId(5L);
        u.setUsername("juan");
        u.setTenantId(tenantId);
        u.setRol("BARBERO");
        u.setActivo(true);
        return u;
    }

    @Test
    void cambiarLaContrasenaDeUnBarberoRevocaSusSesionesYVaciaLaCache() throws Exception {
        Usuario u = barbero(TENANT);
        when(usuarioRepo.findById(5L)).thenReturn(Optional.of(u));
        when(encoder.encode("NuevaClave123")).thenReturn("hash");
        LocalDateTime antes = LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(2);

        mvc.perform(patch("/api/v1/admin/" + TENANT + "/usuarios/5/password").principal(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"NuevaClave123\"}"))
            .andExpect(status().isOk());

        assertNotNull(u.getPasswordChangedAt());
        org.junit.jupiter.api.Assertions.assertTrue(u.getPasswordChangedAt().isAfter(antes));
        org.junit.jupiter.api.Assertions.assertEquals(0, u.getPasswordChangedAt().getNano(), "sin fracciones: el iat del JWT va en segundos");
        verify(authCache).evict("juan");
    }

    @Test
    void desactivarUnBarberoVaciaSuEntradaDeLaCache() throws Exception {
        Usuario u = barbero(TENANT);
        when(usuarioRepo.findById(5L)).thenReturn(Optional.of(u));

        mvc.perform(delete("/api/v1/admin/" + TENANT + "/usuarios/5").principal(admin)).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertFalse(u.isActivo());
        verify(authCache).evict("juan");
    }

    @Test
    void unUsuarioDeOtroNegocioNoSeTocaNiSeVaciaNada() throws Exception {
        when(usuarioRepo.findById(5L)).thenReturn(Optional.of(barbero("barberia-b")));

        mvc.perform(patch("/api/v1/admin/" + TENANT + "/usuarios/5/password").principal(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"NuevaClave123\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/admin/" + TENANT + "/usuarios/5").principal(admin)).andExpect(status().isNotFound());

        verify(usuarioRepo, never()).save(any());
        verify(authCache, never()).evict(any());
    }

    @Test
    void quienNoEsAdminDelNegocioRecibe403() throws Exception {
        Authentication otro = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("x", "ADMIN", "barberia-b", null), null, List.of());

        mvc.perform(delete("/api/v1/admin/" + TENANT + "/usuarios/5").principal(otro)).andExpect(status().isForbidden());

        verify(authCache, never()).evict(any());
    }
}
