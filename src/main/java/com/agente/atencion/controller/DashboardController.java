package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.TenantClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    @Autowired private TurnoRepository turnoRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private TenantClockService tenantClockService;
    @Autowired private com.agente.atencion.service.DashboardService dashboardService;

    @GetMapping("/{tenantId}/hoy")
    public ResponseEntity<?> hoy(@PathVariable String tenantId, Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())
                || !"ADMIN".equals(u.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        return ResponseEntity.ok(dashboardService.hoy(tenantId, hoy));
    }

    @GetMapping("/{tenantId}/rango")
    public ResponseEntity<?> rango(
            @PathVariable String tenantId,
            @RequestParam String desde,
            @RequestParam String hasta,
            Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())
                || !"ADMIN".equals(u.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        LocalDate fechaDesde = LocalDate.parse(desde);
        LocalDate fechaHasta = LocalDate.parse(hasta);
        if (ChronoUnit.DAYS.between(fechaDesde, fechaHasta) > 92) {
            return ResponseEntity.badRequest().body(Map.of("error", "El rango máximo permitido es 92 días"));
        }
        return ResponseEntity.ok(dashboardService.rango(tenantId, fechaDesde, fechaHasta));
    }

    @GetMapping("/{tenantId}/mis-stats")
    public ResponseEntity<?> misStats(
            @PathVariable String tenantId,
            @RequestParam String desde,
            @RequestParam String hasta,
            Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())
                || !"BARBERO".equals(u.rol())
                || u.barberoId() == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        LocalDate fechaDesde = LocalDate.parse(desde);
        LocalDate fechaHasta = LocalDate.parse(hasta);
        List<Turno> turnos = turnoRepository
            .findByTenantIdAndBarberoIdAndFechaBetweenOrderByFechaAscHoraAsc(
                tenantId, u.barberoId(), fechaDesde, fechaHasta
            );

        long total = turnos.size();
        long completados = turnos.stream().filter(t -> "COMPLETADO".equals(t.getEstado())).count();
        long cancelados  = turnos.stream().filter(t -> "CANCELADO".equals(t.getEstado())).count();
        long reservados  = turnos.stream().filter(t -> "RESERVADO".equals(t.getEstado())).count();

        var preciosPorNombre = servicioRepository.findByTenantIdAndActivoTrue(tenantId)
            .stream().collect(Collectors.toMap(s -> s.getNombre(), s -> s.getPrecio(), (a, b) -> a));

        double ingresoEstimado = turnos.stream()
            .filter(t -> !"CANCELADO".equals(t.getEstado()) && t.getServicio() != null)
            .mapToDouble(t -> preciosPorNombre.getOrDefault(t.getServicio(), 0.0))
            .sum();

        var topServicios = turnos.stream()
            .filter(t -> t.getServicio() != null && !"CANCELADO".equals(t.getEstado()))
            .collect(Collectors.groupingBy(Turno::getServicio, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> Map.of("servicio", e.getKey(), "cantidad", e.getValue()))
            .toList();

        var serieDiaria = turnos.stream()
            .collect(Collectors.groupingBy(Turno::getFecha))
            .entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> Map.<String, Object>of(
                "fecha", e.getKey().toString(),
                "total", (long) e.getValue().size(),
                "completados", e.getValue().stream().filter(x -> "COMPLETADO".equals(x.getEstado())).count()
            ))
            .toList();

        return ResponseEntity.ok(Map.of(
            "desde", desde,
            "hasta", hasta,
            "total", total,
            "completados", completados,
            "cancelados", cancelados,
            "reservados", reservados,
            "ingresoEstimado", (long) ingresoEstimado,
            "topServicios", topServicios,
            "serieDiaria", serieDiaria
        ));
    }
}
