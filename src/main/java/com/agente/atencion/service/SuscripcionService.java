package com.agente.atencion.service;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class SuscripcionService {

    public static final String DEMO_SLUG = "barberia-demo";
    public static final int DIAS_GRACIA = 3;
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");

    public enum EstadoAcceso { ACTIVO, DESACTIVADO, VENCIDO }

    private final TenantRepository tenantRepo;
    private final TenantConfigService tenantConfigService;

    public SuscripcionService(TenantRepository tenantRepo, TenantConfigService tenantConfigService) {
        this.tenantRepo = tenantRepo;
        this.tenantConfigService = tenantConfigService;
    }

    public static EstadoAcceso estado(Tenant t) {
        return estado(t, LocalDate.now(ZONA));
    }

    static EstadoAcceso estado(Tenant t, LocalDate hoy) {
        if (!t.isActivo()) return EstadoAcceso.DESACTIVADO;
        LocalDate vencimiento = t.getFechaVencimientoSuscripcion();
        if (vencimiento != null && !DEMO_SLUG.equals(t.getId()) && hoy.isAfter(vencimiento.plusDays(DIAS_GRACIA))) {
            return EstadoAcceso.VENCIDO;
        }
        return EstadoAcceso.ACTIVO;
    }

    @Transactional
    public Tenant extender(String tenantId, TipoCodigo plan, String moneda) {
        return extender(tenantId, plan, moneda, LocalDate.now(ZONA));
    }

    Tenant extender(String tenantId, TipoCodigo plan, String moneda, LocalDate hoy) {
        Tenant t = tenantRepo.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado."));
        LocalDate actual = t.getFechaVencimientoSuscripcion();
        LocalDate base = actual != null && actual.isAfter(hoy) ? actual : hoy;
        t.setFechaRegistro(base);
        t.setFechaVencimientoSuscripcion(base.plusMonths(plan.meses));
        t.setPlanTipo(plan);
        if (moneda != null) t.setMoneda(moneda);
        Tenant saved = tenantRepo.save(t);
        tenantConfigService.evictTenant(tenantId);
        return saved;
    }

    @Transactional
    public Tenant fijar(String tenantId, LocalDate inicio, TipoCodigo plan) {
        Tenant t = tenantRepo.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado."));
        t.setFechaRegistro(inicio);
        t.setFechaVencimientoSuscripcion(inicio.plusMonths(plan.meses));
        t.setPlanTipo(plan);
        Tenant saved = tenantRepo.save(t);
        tenantConfigService.evictTenant(tenantId);
        return saved;
    }
}
