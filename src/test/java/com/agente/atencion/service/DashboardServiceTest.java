package com.agente.atencion.service;

import com.agente.atencion.config.CacheConfig;
import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TurnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 10);

    private TurnoRepository turnos;
    private ServicioRepository servicios;
    private BarberoRepository barberos;
    private CacheManager cacheManager;
    private DashboardService servicio;
    private DisponibilidadCache disponibilidad;

    @BeforeEach
    void setUp() {
        turnos = mock(TurnoRepository.class);
        servicios = mock(ServicioRepository.class);
        barberos = mock(BarberoRepository.class);
        cacheManager = new CacheConfig().cacheManager();
        servicio = new DashboardService(turnos, servicios, barberos, cacheManager);
        disponibilidad = new DisponibilidadCache(cacheManager);

        Servicio corte = new Servicio();
        corte.setNombre("Corte");
        corte.setPrecio(300.0);
        Barbero juan = new Barbero();
        juan.setId(1L);
        juan.setNombre("Juan");
        when(servicios.findByTenantIdAndActivoTrue("a")).thenReturn(List.of(corte));
        when(barberos.findByTenantId("a")).thenReturn(List.of(juan));
        when(turnos.contarPorBarberoEstadoServicio(eq("a"), any(), any()))
            .thenReturn(List.<Object[]>of(new Object[] {1L, "COMPLETADO", "Corte", 2L}));
    }

    @Test
    void hoyTraeUnaSolaAgregacionYDevuelveLasClavesDeSiempre() {
        Map<String, Object> r = servicio.hoy("a", DIA);

        assertEquals(List.of("fecha", "total", "reservados", "completados", "cancelados",
            "ingresoEstimado", "ingresoReal", "porBarbero"), List.copyOf(r.keySet()));
        assertEquals(2L, r.get("total"));
        assertEquals(600L, r.get("ingresoReal"));
        verify(turnos, times(1)).contarPorBarberoEstadoServicio("a", DIA, DIA);
    }

    @Test
    void laSegundaConsultaDentroDelTtlNoVaALaBase() {
        servicio.hoy("a", DIA);
        servicio.hoy("a", DIA);

        verify(turnos, times(1)).contarPorBarberoEstadoServicio("a", DIA, DIA);
    }

    @Test
    void vaciarElNegocioForzaUnaNuevaLectura() {
        servicio.hoy("a", DIA);
        disponibilidad.evictTenant("a");
        servicio.hoy("a", DIA);

        verify(turnos, times(2)).contarPorBarberoEstadoServicio("a", DIA, DIA);
    }

    @Test
    void vaciarUnNegocioNoAfectaAOtro() {
        when(turnos.contarPorBarberoEstadoServicio(eq("b"), any(), any())).thenReturn(List.of());
        servicio.hoy("a", DIA);
        servicio.hoy("b", DIA);

        disponibilidad.evictTenant("b");
        servicio.hoy("a", DIA);

        verify(turnos, times(1)).contarPorBarberoEstadoServicio("a", DIA, DIA);
    }

    @Test
    void rangoIncluyeRankingsYSerie() {
        when(turnos.findRankingServicios("a", DIA, DIA)).thenReturn(List.<Object[]>of(new Object[] {"Corte", 2L}));
        when(turnos.findSerieDiaria("a", DIA, DIA)).thenReturn(List.<Object[]>of(new Object[] {DIA, 2L, 2L}));

        Map<String, Object> r = servicio.rango("a", DIA, DIA);

        assertEquals(List.of("desde", "hasta", "total", "completados", "cancelados", "reservados",
            "ingresoEstimado", "ingresoReal", "rankingBarberos", "rankingServicios", "serieDiaria"),
            List.copyOf(r.keySet()));
        assertEquals(1, ((List<?>) r.get("rankingServicios")).size());
        assertEquals(1, ((List<?>) r.get("serieDiaria")).size());
    }
}
