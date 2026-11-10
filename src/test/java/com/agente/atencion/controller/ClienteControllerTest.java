package com.agente.atencion.controller;

import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClienteControllerTest {

    private TurnoRepository repo;
    private MockMvc mvc;
    private Authentication admin;

    @BeforeEach
    void setUp() {
        repo = mock(TurnoRepository.class);
        ClienteController controlador = new ClienteController();
        ReflectionTestUtils.setField(controlador, "turnoRepository", repo);
        mvc = MockMvcBuilders.standaloneSetup(controlador).build();
        admin = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("a", "ADMIN", "t1", null), null, List.of());
    }

    @Test
    void devuelveLaPaginaPedidaYAvisaSiHayMas() throws Exception {
        List<Object[]> filas = List.<Object[]>of(new Object[] {"Ana", "099", 3L, LocalDate.of(2026, 11, 1)});
        when(repo.buscarClientes(eq("t1"), eq("%"), any(Pageable.class))).thenReturn(new SliceImpl<>(filas, org.springframework.data.domain.PageRequest.of(0, 1), true));

        mvc.perform(get("/api/v1/clientes/t1").principal(admin))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Has-More", "true"))
            .andExpect(jsonPath("$[0].nombre").value("Ana"))
            .andExpect(jsonPath("$[0].totalVisitas").value(3))
            .andExpect(jsonPath("$[0].ultimaVisita").value("2026-11-01"));
    }

    @Test
    void elTamanoSeAcotaYLaBusquedaEscapaLosComodines() throws Exception {
        when(repo.buscarClientes(any(), any(), any())).thenReturn(new SliceImpl<>(List.of()));

        mvc.perform(get("/api/v1/clientes/t1").param("q", " 50%_Ana ").param("size", "99999").param("page", "-4").principal(admin))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Has-More", "false"));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<String> patron = ArgumentCaptor.forClass(String.class);
        verify(repo).buscarClientes(eq("t1"), patron.capture(), pagina.capture());
        assertEquals(200, pagina.getValue().getPageSize());
        assertEquals(0, pagina.getValue().getPageNumber());
        assertEquals("%50\\%\\_ana%", patron.getValue());
    }

    @Test
    void otroNegocioRecibe403() throws Exception {
        mvc.perform(get("/api/v1/clientes/otro").principal(admin)).andExpect(status().isForbidden());
    }
}
