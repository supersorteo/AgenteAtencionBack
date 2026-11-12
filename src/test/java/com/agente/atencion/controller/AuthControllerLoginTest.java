package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.AuthCacheService;
import com.agente.atencion.security.JwtUtil;
import com.agente.atencion.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerLoginTest {

    private MockMvc mvc;
    private PasswordEncoder encoder;
    private UsuarioRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(repo, mock(TenantRepository.class),
            new JwtUtil("clave-de-prueba-con-mas-de-treinta-y-dos-caracteres"), encoder,
            mock(AuthCacheService.class), new LoginAttemptService())).build();
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"ana\",\"password\":\"" + password + "\",\"tenantId\":\"t1\"}"));
    }

    @Test
    void tras5ClavesIncorrectasResponde429AunqueLaSextaSeaCorrecta() throws Exception {
        Usuario u = new Usuario();
        u.setUsername("ana");
        u.setRol("ADMIN");
        u.setTenantId("t1");
        u.setPassword("hash");
        when(repo.findByUsernameAndActivoTrue("ana")).thenReturn(Optional.of(u));
        when(encoder.matches("mala", "hash")).thenReturn(false);

        for (int i = 0; i < 5; i++) login("mala").andExpect(status().isUnauthorized());

        when(encoder.matches("buena", "hash")).thenReturn(true);
        login("buena").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }
}
