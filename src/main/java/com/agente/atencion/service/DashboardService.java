package com.agente.atencion.service;

import com.agente.atencion.config.CacheConfig;
import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TurnoRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class DashboardService {

    private final TurnoRepository turnos;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final CacheManager cacheManager;

    public DashboardService(TurnoRepository turnos, ServicioRepository servicios,
                            BarberoRepository barberos, CacheManager cacheManager) {
        this.turnos = turnos;
        this.servicios = servicios;
        this.barberos = barberos;
        this.cacheManager = cacheManager;
    }

    public Map<String, Object> hoy(String tenantId, LocalDate fecha) {
        return cacheado(tenantId + "_hoy_" + fecha, () -> {
            DashboardCalculator.Resumen r = resumen(tenantId, fecha, fecha);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fecha", fecha.toString());
            out.put("total", r.total());
            out.put("reservados", r.reservados());
            out.put("completados", r.completados());
            out.put("cancelados", r.cancelados());
            out.put("ingresoEstimado", r.ingresoEstimado());
            out.put("ingresoReal", r.ingresoReal());
            out.put("porBarbero", DashboardCalculator.sinIngreso(r.barberos()));
            return out;
        });
    }

    public Map<String, Object> rango(String tenantId, LocalDate desde, LocalDate hasta) {
        return cacheado(tenantId + "_rango_" + desde + "_" + hasta, () -> {
            DashboardCalculator.Resumen r = resumen(tenantId, desde, hasta);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("desde", desde.toString());
            out.put("hasta", hasta.toString());
            out.put("total", r.total());
            out.put("completados", r.completados());
            out.put("cancelados", r.cancelados());
            out.put("reservados", r.reservados());
            out.put("ingresoEstimado", r.ingresoEstimado());
            out.put("ingresoReal", r.ingresoReal());
            out.put("rankingBarberos", DashboardCalculator.rankingPorTotal(r.barberos()));
            out.put("rankingServicios", turnos.findRankingServicios(tenantId, desde, hasta).stream()
                .limit(5)
                .map(row -> Map.of("servicio", row[0], "cantidad", row[1]))
                .toList());
            out.put("serieDiaria", turnos.findSerieDiaria(tenantId, desde, hasta).stream()
                .map(row -> Map.<String, Object>of(
                    "fecha", row[0].toString(),
                    "total", row[1],
                    "completados", row[2] != null ? row[2] : 0L))
                .toList());
            return out;
        });
    }

    public Map<String, Object> clientes(String tenantId) {
        return cacheado(tenantId + "_clientes", () -> {
            Object[] f = turnos.resumenClientes(tenantId).stream().findFirst().orElse(new Object[] {0L, 0L, 0L});
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", ((Number) f[0]).longValue());
            out.put("nuevos", ((Number) f[1]).longValue());
            out.put("recurrentes", ((Number) f[2]).longValue());
            return out;
        });
    }

    private DashboardCalculator.Resumen resumen(String tenantId, LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = turnos.contarPorBarberoEstadoServicio(tenantId, desde, hasta);
        Map<String, Double> precios = new HashMap<>();
        for (Servicio s : servicios.findByTenantIdAndActivoTrue(tenantId)) {
            precios.putIfAbsent(s.getNombre(), s.getPrecio() == null ? 0.0 : s.getPrecio());
        }
        Map<Long, String> nombres = new HashMap<>();
        for (Barbero b : barberos.findByTenantId(tenantId)) nombres.putIfAbsent(b.getId(), b.getNombre());
        return DashboardCalculator.calcular(filas, precios, nombres);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cacheado(String clave, Supplier<Map<String, Object>> cargador) {
        Cache cache = cacheManager.getCache(CacheConfig.DASHBOARD);
        if (cache == null) return cargador.get();
        return (Map<String, Object>) cache.get(clave, cargador::get);
    }
}
