package com.agente.atencion.service;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.service.SuscripcionService.EstadoAcceso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 10);

    @Mock TenantRepository tenantRepo;
    @Mock TenantConfigService tenantConfigService;
    @InjectMocks SuscripcionService service;

    private Tenant tenant(String id, boolean activo, LocalDate vencimiento) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setActivo(activo);
        t.setFechaVencimientoSuscripcion(vencimiento);
        return t;
    }

    @Test
    void sinVencimiento_esPermanente() {
        assertEquals(EstadoAcceso.ACTIVO, SuscripcionService.estado(tenant("a", true, null), HOY));
    }

    @Test
    void desactivado_manda_sobreElVencimiento() {
        assertEquals(EstadoAcceso.DESACTIVADO, SuscripcionService.estado(tenant("a", false, HOY.plusMonths(1)), HOY));
    }

    @Test
    void vigente_y_dentroDeLaGracia_siguenActivos() {
        assertEquals(EstadoAcceso.ACTIVO, SuscripcionService.estado(tenant("a", true, HOY.plusDays(5)), HOY));
        assertEquals(EstadoAcceso.ACTIVO, SuscripcionService.estado(tenant("a", true, HOY.minusDays(3)), HOY));
    }

    @Test
    void pasadaLaGracia_estaVencido() {
        assertEquals(EstadoAcceso.VENCIDO, SuscripcionService.estado(tenant("a", true, HOY.minusDays(4)), HOY));
    }

    @Test
    void laDemoNuncaVence() {
        assertEquals(EstadoAcceso.ACTIVO,
            SuscripcionService.estado(tenant(SuscripcionService.DEMO_SLUG, true, HOY.minusYears(1)), HOY));
    }

    @Test
    void extender_conSuscripcionVigente_sumaSobreElVencimientoActual() {
        Tenant t = tenant("a", true, HOY.plusDays(10));
        when(tenantRepo.findById("a")).thenReturn(Optional.of(t));
        when(tenantRepo.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        Tenant r = service.extender("a", TipoCodigo.PLAN_3M, "ARS", HOY);

        assertEquals(HOY.plusDays(10).plusMonths(3), r.getFechaVencimientoSuscripcion());
        assertEquals(HOY.plusDays(10), r.getFechaRegistro());
        assertEquals(TipoCodigo.PLAN_3M, r.getPlanTipo());
        assertEquals("ARS", r.getMoneda());
        verify(tenantConfigService).evictTenant("a");
    }

    @Test
    void extender_conSuscripcionVencida_cuentaDesdeHoy() {
        Tenant t = tenant("a", true, HOY.minusMonths(2));
        when(tenantRepo.findById("a")).thenReturn(Optional.of(t));
        when(tenantRepo.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        Tenant r = service.extender("a", TipoCodigo.PLAN_1M, null, HOY);

        assertEquals(HOY.plusMonths(1), r.getFechaVencimientoSuscripcion());
    }

    @Test
    void extender_sinVencimientoPrevio_cuentaDesdeHoy() {
        Tenant t = tenant("a", true, null);
        when(tenantRepo.findById("a")).thenReturn(Optional.of(t));
        when(tenantRepo.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(HOY.plusMonths(12), service.extender("a", TipoCodigo.PLAN_12M, "USD", HOY).getFechaVencimientoSuscripcion());
    }
}
