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
import java.util.ArrayList;
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

    @GetMapping("/{tenantId}/hoy")
    public ResponseEntity<?> hoy(@PathVariable String tenantId, Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())
                || !"ADMIN".equals(u.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        List<Turno> turnos = turnoRepository.findByTenantIdAndFecha(tenantId, hoy);

        long total = turnos.size();
        long reservados = turnos.stream().filter(t -> "RESERVADO".equals(t.getEstado())).count();
        long completados = turnos.stream().filter(t -> "COMPLETADO".equals(t.getEstado())).count();
        long cancelados = turnos.stream().filter(t -> "CANCELADO".equals(t.getEstado())).count();

        // Ingreso estimado: suma precios de servicios completados o reservados
        var preciosPorNombre = servicioRepository.findByTenantIdAndActivoTrue(tenantId)
            .stream().collect(Collectors.toMap(s -> s.getNombre(), s -> s.getPrecio(), (a, b) -> a));

        double ingresoEstimado = turnos.stream()
            .filter(t -> !"CANCELADO".equals(t.getEstado()) && t.getServicio() != null)
            .mapToDouble(t -> preciosPorNombre.getOrDefault(t.getServicio(), 0.0))
            .sum();

        // Desglose por barbero
        var barberosPorId = barberoRepository.findByTenantId(tenantId)
            .stream().collect(Collectors.toMap(b -> b.getId(), b -> b.getNombre(), (a, b) -> a));

        var porBarbero = turnos.stream()
            .filter(t -> t.getBarberoId() != null)
            .collect(Collectors.groupingBy(Turno::getBarberoId))
            .entrySet().stream()
            .map(e -> Map.of(
                "barberoId", e.getKey(),
                "nombre", barberosPorId.getOrDefault(e.getKey(), "Barbero #" + e.getKey()),
                "total", e.getValue().size(),
                "completados", e.getValue().stream().filter(t -> "COMPLETADO".equals(t.getEstado())).count()
            ))
            .toList();

        return ResponseEntity.ok(Map.of(
            "fecha", hoy.toString(),
            "total", total,
            "reservados", reservados,
            "completados", completados,
            "cancelados", cancelados,
            "ingresoEstimadoUYU", (long) ingresoEstimado,
            "porBarbero", porBarbero
        ));
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
        List<Turno> turnos = turnoRepository.findByTenantIdAndFechaBetweenOrderByFechaAscHoraAsc(tenantId, fechaDesde, fechaHasta);

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

        var barberosPorId = barberoRepository.findByTenantId(tenantId)
            .stream().collect(Collectors.toMap(b -> b.getId(), b -> b.getNombre(), (a, b) -> a));

        // Ranking de barberos
        var rankingBarberos = turnos.stream()
            .filter(t -> t.getBarberoId() != null)
            .collect(Collectors.groupingBy(Turno::getBarberoId))
            .entrySet().stream()
            .map(e -> {
                long comp = e.getValue().stream().filter(t -> "COMPLETADO".equals(t.getEstado())).count();
                double ing = e.getValue().stream()
                    .filter(t -> !"CANCELADO".equals(t.getEstado()) && t.getServicio() != null)
                    .mapToDouble(t -> preciosPorNombre.getOrDefault(t.getServicio(), 0.0)).sum();
                return Map.of(
                    "barberoId", e.getKey(),
                    "nombre", barberosPorId.getOrDefault(e.getKey(), "Barbero #" + e.getKey()),
                    "total", (long) e.getValue().size(),
                    "completados", comp,
                    "ingreso", (long) ing
                );
            })
            .sorted(Comparator.comparingLong(m -> -((Long) m.get("total"))))
            .toList();

        // Ranking de servicios
        var rankingServicios = turnos.stream()
            .filter(t -> t.getServicio() != null && !"CANCELADO".equals(t.getEstado()))
            .collect(Collectors.groupingBy(Turno::getServicio, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> Map.of("servicio", e.getKey(), "cantidad", e.getValue()))
            .toList();

        // Serie diaria (para mini gráfico)
        var serieDiaria = new ArrayList<Map<String, Object>>();
        for (LocalDate d = fechaDesde; !d.isAfter(fechaHasta); d = d.plusDays(1)) {
            final LocalDate fd = d;
            long t = turnos.stream().filter(x -> x.getFecha().equals(fd)).count();
            long c = turnos.stream().filter(x -> x.getFecha().equals(fd) && "COMPLETADO".equals(x.getEstado())).count();
            if (t > 0) serieDiaria.add(Map.of("fecha", fd.toString(), "total", t, "completados", c));
        }

        return ResponseEntity.ok(Map.of(
            "desde", desde,
            "hasta", hasta,
            "total", total,
            "completados", completados,
            "cancelados", cancelados,
            "reservados", reservados,
            "ingresoEstimado", (long) ingresoEstimado,
            "rankingBarberos", rankingBarberos,
            "rankingServicios", rankingServicios,
            "serieDiaria", serieDiaria
        ));
    }
}
