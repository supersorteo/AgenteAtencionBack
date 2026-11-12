package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.TenantClockService;
import com.agente.atencion.service.TurnoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TurnoControllerTest {

    private static final String TENANT = "barberia-demo";
    private static final String RESERVA = """
        {"barberoId":1,"servicio":"Corte de cabello","fecha":"2026-09-23","hora":"10:00","paciente":"Juan García","telefono":"099123456"}
        """;

    MockMvc mvc;

    @Mock TurnoRepository turnoRepository;
    @Mock TurnoService turnoService;
    @Mock TenantClockService tenantClockService;
    @InjectMocks TurnoController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
    }

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("admin", "ADMIN", TENANT, null), null, List.of());
    }

    @Test
    void post_reservaValida_retorna200ConElTurnoGuardado() throws Exception {
        Turno guardado = new Turno();
        guardado.setId(42L);
        guardado.setHoraFin("10:30");
        when(turnoService.crear(eq(TENANT), any())).thenReturn(guardado);

        mvc.perform(post("/api/v1/turnos/" + TENANT).contentType(MediaType.APPLICATION_JSON).content(RESERVA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.horaFin").value("10:30"))
            .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void post_slotOcupado_propagaElConflictoDelServicio() throws Exception {
        when(turnoService.crear(eq(TENANT), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "El horario ya no está disponible"));

        mvc.perform(post("/api/v1/turnos/" + TENANT).contentType(MediaType.APPLICATION_JSON).content(RESERVA))
            .andExpect(status().isConflict());
    }

    @Test
    void post_conHoraConFormatoInvalido_retorna400SinLlamarAlServicio() throws Exception {
        mvc.perform(post("/api/v1/turnos/" + TENANT).contentType(MediaType.APPLICATION_JSON)
                .content(RESERVA.replace("10:00", "25:99")))
            .andExpect(status().isBadRequest());

        verify(turnoService, never()).crear(any(), any());
    }

    @Test
    void get_conFecha_retornaListaFiltrada() throws Exception {
        when(turnoRepository.findByTenantIdAndFecha(TENANT, LocalDate.of(2026, 9, 23))).thenReturn(List.of());

        mvc.perform(get("/api/v1/turnos/" + TENANT).param("fecha", "2026-09-23").principal(admin()))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void get_deOtroNegocio_retorna403() throws Exception {
        mvc.perform(get("/api/v1/turnos/otro-negocio").principal(admin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void put_estado_delegaEnElServicioYDevuelveElTurno() throws Exception {
        Turno actualizado = new Turno();
        actualizado.setId(1L);
        actualizado.setEstado("COMPLETADO");
        when(turnoService.actualizarEstado(eq(TENANT), eq(1L), eq("COMPLETADO"), any())).thenReturn(Optional.of(actualizado));

        mvc.perform(put("/api/v1/turnos/" + TENANT + "/1/estado").principal(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"COMPLETADO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("COMPLETADO"));
    }

    @Test
    void put_estado_deUnTurnoInexistente_retorna404() throws Exception {
        when(turnoService.actualizarEstado(any(), any(), any(), any())).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/turnos/" + TENANT + "/9/estado").principal(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"COMPLETADO\"}"))
            .andExpect(status().isNotFound());
    }
}
