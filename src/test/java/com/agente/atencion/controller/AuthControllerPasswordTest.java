package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.AuthCacheService;
import com.agente.atencion.security.JwtUtil;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerPasswordTest {

    private final JwtUtil jwt = new JwtUtil("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres");
    private UsuarioRepository repo;
    private PasswordEncoder encoder;
    private AuthCacheService authCache;
    private MockMvc mvc;
    private Authentication auth;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        authCache = mock(AuthCacheService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(repo, mock(TenantRepository.class), jwt, encoder, authCache, new com.agente.atencion.security.LoginAttemptService())).build();
        auth = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("ana", "ADMIN", "t1", null), null, List.of());
        usuario = new Usuario();
        usuario.setUsername("ana");
        usuario.setRol("ADMIN");
        usuario.setTenantId("t1");
        usuario.setPassword("hash-viejo");
        when(repo.findByUsernameAndActivoTrue("ana")).thenReturn(Optional.of(usuario));
        when(encoder.matches("ClaveActual1", "hash-viejo")).thenReturn(true);
        when(encoder.encode("ClaveNueva123")).thenReturn("hash-nuevo");
    }

    private org.springframework.test.web.servlet.ResultActions cambiar(String nueva) throws Exception {
        return mvc.perform(patch("/api/auth/cambiar-password").principal(auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"actual\":\"ClaveActual1\",\"nueva\":\"" + nueva + "\"}"));
    }

    @Test
    void cambiarLaClaveRevocaLasSesionesViejasYEntregaUnTokenValido() throws Exception {
        cambiar("ClaveNueva123").andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());

        assertEquals("hash-nuevo", usuario.getPassword());
        assertNotNull(usuario.getPasswordChangedAt());
        assertEquals(0, usuario.getPasswordChangedAt().getNano());
        verify(authCache).evict("ana");
    }

    @Test
    void unaClaveCortaSeRechazaSinTocarNada() throws Exception {
        cambiar("corta").andExpect(status().isBadRequest());

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
        assertTrue(usuario.getPasswordChangedAt() == null);
    }

    @Test
    void conLaClaveActualIncorrectaResponde401() throws Exception {
        when(encoder.matches("ClaveActual1", "hash-viejo")).thenReturn(false);

        cambiar("ClaveNueva123").andExpect(status().isUnauthorized());
    }
}
