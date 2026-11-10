package com.agente.atencion.service;

import com.agente.atencion.dto.TurnoCrearRequest;
import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnoServiceTest {

    private static final String TENANT = "barberia-demo";
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 23);

    @Mock TurnoRepository turnoRepository;
    @Mock BarberoRepository barberoRepository;
    @Mock DisponibilidadService disponibilidadService;
    @Mock TenantClockService tenantClockService;
    @Mock TenantConfigService tenantConfigService;
    @InjectMocks TurnoService service;

    private TurnoCrearRequest request;

    @BeforeEach
    void setUp() {
        request = new TurnoCrearRequest(1L, "Corte", FECHA, "10:00", "Juan", "+59899111222");
    }

    private Tenant tenant(boolean activo) {
        Tenant t = new Tenant();
        t.setId(TENANT);
        t.setActivo(activo);
        return t;
    }

    private void relojFijo() {
        when(tenantClockService.clockFor(TENANT)).thenReturn(
            Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneId.of("America/Montevideo")));
    }

    private ResponseStatusException assertRechazo(HttpStatus esperado) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.crear(TENANT, request));
        assertEquals(esperado, ex.getStatusCode());
        verify(turnoRepository, never()).save(any());
        return ex;
    }

    @Test
    void tenantInexistente_404() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(null);
        assertRechazo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tenantInactivo_403() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(false));
        assertRechazo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fechaPasada_400() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        when(tenantClockService.clockFor(TENANT)).thenReturn(
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneId.of("America/Montevideo")));
        assertRechazo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void barberoDeOtroNegocio_400() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        relojFijo();
        when(barberoRepository.bloquearParaReserva(TENANT, 1L)).thenReturn(Optional.empty());
        assertRechazo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void servicioInexistente_400() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        relojFijo();
        when(barberoRepository.bloquearParaReserva(TENANT, 1L)).thenReturn(Optional.of(new Barbero()));
        when(disponibilidadService.buscarDuracion(TENANT, "Corte")).thenReturn(OptionalInt.empty());
        assertRechazo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void horaFueraDeHorarioOGrilla_400() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        relojFijo();
        when(barberoRepository.bloquearParaReserva(TENANT, 1L)).thenReturn(Optional.of(new Barbero()));
        when(disponibilidadService.buscarDuracion(TENANT, "Corte")).thenReturn(OptionalInt.of(30));
        when(disponibilidadService.slotValido(TENANT, 1L, FECHA, "10:00", 30)).thenReturn(false);
        assertRechazo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void slotOcupado_409() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        relojFijo();
        when(barberoRepository.bloquearParaReserva(TENANT, 1L)).thenReturn(Optional.of(new Barbero()));
        when(disponibilidadService.buscarDuracion(TENANT, "Corte")).thenReturn(OptionalInt.of(30));
        when(disponibilidadService.slotValido(TENANT, 1L, FECHA, "10:00", 30)).thenReturn(true);
        when(disponibilidadService.calcularHoraFin("10:00", 30)).thenReturn("10:30");
        when(disponibilidadService.validar(1L, FECHA, "10:00", "10:30")).thenReturn(false);
        assertRechazo(HttpStatus.CONFLICT);
    }

    @Test
    void reservaValida_guardaConHoraFin() {
        when(tenantConfigService.getTenant(TENANT)).thenReturn(tenant(true));
        relojFijo();
        when(barberoRepository.bloquearParaReserva(TENANT, 1L)).thenReturn(Optional.of(new Barbero()));
        when(disponibilidadService.buscarDuracion(TENANT, "Corte")).thenReturn(OptionalInt.of(30));
        when(disponibilidadService.slotValido(TENANT, 1L, FECHA, "10:00", 30)).thenReturn(true);
        when(disponibilidadService.calcularHoraFin("10:00", 30)).thenReturn("10:30");
        when(disponibilidadService.validar(1L, FECHA, "10:00", "10:30")).thenReturn(true);
        when(turnoRepository.save(any(Turno.class))).thenAnswer(i -> i.getArgument(0));

        Turno guardado = service.crear(TENANT, request);

        assertEquals("10:30", guardado.getHoraFin());
        assertEquals("RESERVADO", guardado.getEstado());
        assertEquals(TENANT, guardado.getTenantId());
    }
}
