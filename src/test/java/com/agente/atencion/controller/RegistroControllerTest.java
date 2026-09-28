package com.agente.atencion.controller;

import com.agente.atencion.dto.RegistroRequest;
import com.agente.atencion.dto.RegistroResponse;
import com.agente.atencion.service.RegistroService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RegistroControllerTest {

    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();

    @Mock RegistroService registroService;
    @InjectMocks RegistroController controller;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void registrarRetorna201ConDatosValidos() throws Exception {
        when(registroService.registrar(any())).thenReturn(
            new RegistroResponse("mi-barberia", "juan", "/mi-barberia/admin", "¡Bienvenido!")
        );

        mvc.perform(post("/api/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombreNegocio":"Mi Barberia","adminNombre":"Juan","username":"juan","password":"pass1234","codigoInvitacion":"BARBEROS2026"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("mi-barberia"))
            .andExpect(jsonPath("$.username").value("juan"));
    }

    @Test
    void registrarRetorna409SiSlugYaExiste() throws Exception {
        when(registroService.registrar(any())).thenThrow(
            new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un negocio con ese nombre.")
        );

        mvc.perform(post("/api/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombreNegocio":"Mi Barberia","adminNombre":"Juan","username":"juan","password":"pass1234","codigoInvitacion":"BARBEROS2026"}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void registrarRetorna403SiCodigoInvalido() throws Exception {
        when(registroService.registrar(any())).thenThrow(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Código de invitación incorrecto.")
        );

        mvc.perform(post("/api/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombreNegocio":"Mi Barberia","adminNombre":"Juan","username":"juan","password":"pass1234","codigoInvitacion":"MALO"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void registrarRetorna400SiPasswordCorta() throws Exception {
        when(registroService.registrar(any())).thenThrow(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres.")
        );

        mvc.perform(post("/api/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombreNegocio":"Mi Barberia","adminNombre":"Juan","username":"juan","password":"abc","codigoInvitacion":"BARBEROS2026"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
