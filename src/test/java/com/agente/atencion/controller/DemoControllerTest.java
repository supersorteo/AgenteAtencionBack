package com.agente.atencion.controller;

import com.agente.atencion.entity.DemoCredencial;
import com.agente.atencion.service.DemoCredencialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoControllerTest {

    private DemoCredencialService servicio;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        servicio = mock(DemoCredencialService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DemoController(servicio)).build();
    }

    @Test
    void devuelveUsuarioYContrasenaDeLaDemo() throws Exception {
        DemoCredencial c = new DemoCredencial();
        c.setTenantId("barberia-demo");
        c.setUsername("admin");
        c.setPassword("admin123");
        when(servicio.obtener()).thenReturn(Optional.of(c));

        mvc.perform(get("/api/v1/demo/credenciales"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.password").value("admin123"))
            .andExpect(jsonPath("$.tenantId").doesNotExist());
    }

    @Test
    void sinCredencialesConfiguradasResponde404() throws Exception {
        when(servicio.obtener()).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/demo/credenciales")).andExpect(status().isNotFound());
    }
}
