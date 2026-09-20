package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.service.DisponibilidadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TurnoControllerTest {

    MockMvc mvc;
    ObjectMapper json;

    @Mock TurnoRepository turnoRepository;
    @Mock DisponibilidadService disponibilidadService;
    @InjectMocks TurnoController controller;

    private static final String TENANT = "barberia-demo";

    @BeforeEach
    void setUp() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
    }

    @Test
    void post_slotDisponible_retorna200ConHoraFin() throws Exception {
        Turno entrada = turnoConBarbero("Juan García", "Corte de cabello", "2026-09-23", "10:00", 1L);

        Turno guardado = turnoConBarbero("Juan García", "Corte de cabello", "2026-09-23", "10:00", 1L);
        guardado.setId(42L);
        guardado.setHoraFin("10:30");

        when(disponibilidadService.obtenerDuracion(TENANT, "Corte de cabello")).thenReturn(30);
        when(disponibilidadService.calcularHoraFin("10:00", 30)).thenReturn("10:30");
        when(disponibilidadService.validar(1L, LocalDate.of(2026, 9, 23), "10:00", "10:30")).thenReturn(true);
        when(turnoRepository.save(any())).thenReturn(guardado);

        mvc.perform(post("/api/v1/turnos/" + TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.horaFin").value("10:30"))
            .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void post_slotOcupado_retorna409() throws Exception {
        Turno entrada = turnoConBarbero("Pedro López", "Corte de cabello", "2026-09-23", "10:00", 1L);

        when(disponibilidadService.obtenerDuracion(TENANT, "Corte de cabello")).thenReturn(30);
        when(disponibilidadService.calcularHoraFin("10:00", 30)).thenReturn("10:30");
        when(disponibilidadService.validar(1L, LocalDate.of(2026, 9, 23), "10:00", "10:30")).thenReturn(false);

        mvc.perform(post("/api/v1/turnos/" + TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void post_sinBarberoId_guardaSinValidarDisponibilidad() throws Exception {
        Turno entrada = new Turno();
        entrada.setPaciente("Carlos");
        entrada.setFecha(LocalDate.of(2026, 9, 23));
        entrada.setHora("10:00");

        Turno guardado = new Turno();
        guardado.setId(7L);
        guardado.setPaciente("Carlos");
        when(turnoRepository.save(any())).thenReturn(guardado);

        mvc.perform(post("/api/v1/turnos/" + TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void get_conFecha_retornaListaFiltrada() throws Exception {
        when(turnoRepository.findByTenantIdAndFecha(TENANT, LocalDate.of(2026, 9, 23)))
            .thenReturn(List.of());

        mvc.perform(get("/api/v1/turnos/" + TENANT).param("fecha", "2026-09-23"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @Test
    void put_estado_actualizaCorrectamente() throws Exception {
        Turno existing = turnoConBarbero("Ana", "Corte de cabello", "2026-09-23", "10:00", 1L);
        existing.setId(1L);
        existing.setEstado("RESERVADO");
        existing.setTenantId(TENANT);

        Turno updated = turnoConBarbero("Ana", "Corte de cabello", "2026-09-23", "10:00", 1L);
        updated.setId(1L);
        updated.setEstado("COMPLETADO");

        when(turnoRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(turnoRepository.save(any())).thenReturn(updated);

        mvc.perform(put("/api/v1/turnos/" + TENANT + "/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"COMPLETADO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("COMPLETADO"));
    }

    // ── helper ───────────────────────────────────────────

    private Turno turnoConBarbero(String paciente, String servicio, String fecha, String hora, Long barberoId) {
        Turno t = new Turno();
        t.setPaciente(paciente);
        t.setServicio(servicio);
        t.setFecha(LocalDate.parse(fecha));
        t.setHora(hora);
        t.setBarberoId(barberoId);
        return t;
    }
}
