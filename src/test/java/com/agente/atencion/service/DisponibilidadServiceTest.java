package com.agente.atencion.service;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock private BarberoRepository barberoRepository;
    @Mock private HorarioBarberoRepository horarioRepository;
    @Mock private BloqueoHorarioRepository bloqueoRepository;
    @Mock private TurnoRepository turnoRepository;
    @Mock private ServicioRepository servicioRepository;

    @InjectMocks private DisponibilidadService sut;

    private static final String TENANT = "barberia-demo";
    private static final LocalDate MARTES = LocalDate.of(2026, 9, 22); // día 2

    private Barbero barberoJuan;
    private HorarioBarbero horarioMartes;
    private Servicio servicioCorte;

    @BeforeEach
    void setUp() {
        barberoJuan = new Barbero();
        barberoJuan.setId(1L);
        barberoJuan.setTenantId(TENANT);
        barberoJuan.setNombre("Juan");
        barberoJuan.setActivo(true);

        horarioMartes = new HorarioBarbero();
        horarioMartes.setBarberoId(1L);
        horarioMartes.setDiaSemana(2);
        horarioMartes.setHoraInicio("09:00");
        horarioMartes.setHoraFin("12:00");

        servicioCorte = new Servicio();
        servicioCorte.setNombre("Corte de cabello");
        servicioCorte.setDuracionMinutos(30);
        servicioCorte.setActivo(true);
        servicioCorte.setTenantId(TENANT);
    }

    // ── calcular ─────────────────────────────────────────

    @Test
    void calcular_sinTurnosExistentes_retornaSlotsCadaMedia() {
        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(servicioCorte));
        when(barberoRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.of(horarioMartes));
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of());

        var slots = sut.calcular(TENANT, MARTES, "Corte de cabello", null);

        // 09:00-12:00 con 30 min → 6 slots: 09:00, 09:30, 10:00, 10:30, 11:00, 11:30
        assertThat(slots).hasSize(6);
        assertThat(slots.get(0).hora()).isEqualTo("09:00");
        assertThat(slots.get(0).horaFin()).isEqualTo("09:30");
        assertThat(slots.get(5).hora()).isEqualTo("11:30");
        assertThat(slots.get(5).horaFin()).isEqualTo("12:00");
        assertThat(slots).allMatch(s -> s.barberoNombre().equals("Juan"));
    }

    @Test
    void calcular_barberonNoTrabajaeseaDia_retornaVacio() {
        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(servicioCorte));
        when(barberoRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.empty());

        var slots = sut.calcular(TENANT, MARTES, "Corte de cabello", null);

        assertThat(slots).isEmpty();
    }

    @Test
    void calcular_turnoExistente_bloqueaSuSlotExacto() {
        Turno turno = turnoEn("10:00", "10:30");
        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(servicioCorte));
        when(barberoRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.of(horarioMartes));
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of(turno));

        var slots = sut.calcular(TENANT, MARTES, "Corte de cabello", null);

        // 6 slots - 1 ocupado = 5
        assertThat(slots).hasSize(5);
        assertThat(slots).noneMatch(s -> s.hora().equals("10:00"));
    }

    @Test
    void calcular_turno60min_bloqueaSlotsQueSeSuperponen() {
        // Turno de 60 min a las 10:00 → bloquea 09:30 (09:30-10:30), 10:00 (10:00-11:00), 10:30 (10:30-11:30)
        Servicio corte60 = new Servicio();
        corte60.setNombre("Tratamiento capilar");
        corte60.setDuracionMinutos(60);
        corte60.setActivo(true);
        corte60.setTenantId(TENANT);

        HorarioBarbero horarioLargo = new HorarioBarbero();
        horarioLargo.setBarberoId(1L);
        horarioLargo.setDiaSemana(2);
        horarioLargo.setHoraInicio("09:00");
        horarioLargo.setHoraFin("14:00");

        Turno turnoOcupado = turnoEn("10:00", "11:00");

        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(corte60));
        when(barberoRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.of(horarioLargo));
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of(turnoOcupado));

        var slots = sut.calcular(TENANT, MARTES, "Tratamiento capilar", null);

        // Slots de 60 min posibles: 09:00, 09:30, 10:00, 10:30, 11:00, 11:30, 12:00, 12:30, 13:00
        // Turno 10:00-11:00 bloquea: 09:30 (09:30-10:30), 10:00 (10:00-11:00) — no 09:00 ni 10:30 ni 11:00+
        // Wait: 10:30-11:30 vs turno 10:00-11:00: 10:30 < 11:00 AND 11:30 > 10:00 → conflicto
        // 09:00-10:00 vs turno 10:00-11:00: 09:00 < 11:00 AND 10:00 > 10:00 → 10:00 > 10:00 es false → no conflicto
        // 09:30-10:30 vs turno 10:00-11:00: 09:30 < 11:00 AND 10:30 > 10:00 → conflicto
        // 10:00-11:00 vs turno 10:00-11:00: conflicto
        // 10:30-11:30 vs turno 10:00-11:00: conflicto
        // 11:00-12:00: 11:00 < 11:00 es false → no conflicto ✓
        assertThat(slots).noneMatch(s -> s.hora().equals("09:30"));
        assertThat(slots).noneMatch(s -> s.hora().equals("10:00"));
        assertThat(slots).noneMatch(s -> s.hora().equals("10:30"));
        assertThat(slots).anyMatch(s -> s.hora().equals("09:00"));
        assertThat(slots).anyMatch(s -> s.hora().equals("11:00"));
    }

    @Test
    void calcular_filtroPorBarberoId_soloRetornaEseBarbero() {
        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(servicioCorte));
        when(barberoRepository.findById(1L)).thenReturn(Optional.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.of(horarioMartes));
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of());

        var slots = sut.calcular(TENANT, MARTES, "Corte de cabello", 1L);

        assertThat(slots).isNotEmpty();
        assertThat(slots).allMatch(s -> s.barberoId() == 1L);
        verify(barberoRepository, never()).findByTenantIdAndActivoTrue(any());
    }

    @Test
    void calcular_servicioDesconocido_usaDuracion30PorDefecto() {
        when(servicioRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of());
        when(barberoRepository.findByTenantIdAndActivoTrue(TENANT)).thenReturn(List.of(barberoJuan));
        when(horarioRepository.findByBarberoIdAndDiaSemana(1L, 2)).thenReturn(Optional.of(horarioMartes));
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of());

        var slots = sut.calcular(TENANT, MARTES, "Servicio inexistente", null);

        // 09:00-12:00 con 30 min por defecto → 6 slots
        assertThat(slots).hasSize(6);
    }

    // ── validar ──────────────────────────────────────────

    @Test
    void validar_sinConflictos_retornaTrue() {
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of());
        when(bloqueoRepository.findConflictos(1L, MARTES, "10:00", "10:30")).thenReturn(List.of());

        assertThat(sut.validar(1L, MARTES, "10:00", "10:30")).isTrue();
    }

    @Test
    void validar_turnoExistenteSolapado_retornaFalse() {
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of(turnoEn("10:00", "10:30")));

        assertThat(sut.validar(1L, MARTES, "10:00", "10:30")).isFalse();
    }

    @Test
    void validar_bloqueoCubreSlot_retornaFalse() {
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of());
        when(bloqueoRepository.findConflictos(1L, MARTES, "10:00", "10:30"))
            .thenReturn(List.of(new com.agente.atencion.entity.BloqueoHorario()));

        assertThat(sut.validar(1L, MARTES, "10:00", "10:30")).isFalse();
    }

    @Test
    void validar_turnoAdyacente_sinSolapamiento_retornaTrue() {
        // Turno termina justo cuando el nuevo empieza → no hay solapamiento
        when(turnoRepository.findByBarberoIdAndFecha(1L, MARTES)).thenReturn(List.of(turnoEn("09:00", "10:00")));
        when(bloqueoRepository.findConflictos(1L, MARTES, "10:00", "10:30")).thenReturn(List.of());

        assertThat(sut.validar(1L, MARTES, "10:00", "10:30")).isTrue();
    }

    // ── calcularHoraFin ──────────────────────────────────

    @Test
    void calcularHoraFin_sumaDuracionCorrecta() {
        assertThat(sut.calcularHoraFin("10:00", 30)).isEqualTo("10:30");
        assertThat(sut.calcularHoraFin("10:00", 45)).isEqualTo("10:45");
        assertThat(sut.calcularHoraFin("10:00", 60)).isEqualTo("11:00");
        assertThat(sut.calcularHoraFin("19:30", 30)).isEqualTo("20:00");
    }

    // ── helpers ──────────────────────────────────────────

    private Turno turnoEn(String hora, String horaFin) {
        Turno t = new Turno();
        t.setBarberoId(1L);
        t.setFecha(MARTES);
        t.setHora(hora);
        t.setHoraFin(horaFin);
        t.setEstado("RESERVADO");
        return t;
    }
}
