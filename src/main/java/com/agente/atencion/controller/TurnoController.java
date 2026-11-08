package com.agente.atencion.controller;

import com.agente.atencion.dto.TurnoCrearRequest;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.DisponibilidadService;
import com.agente.atencion.service.TenantClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private static final Logger log = LoggerFactory.getLogger(TurnoController.class);
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadService disponibilidadService;
    @Autowired private TenantClockService tenantClockService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<List<Turno>> listar(@PathVariable String tenantId,
                               @RequestParam(required = false) String fecha, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ADMIN".equals(usuario.rol()) || !tenantId.equals(usuario.tenantId())) {
            log.warn("FORBIDDEN listar-turnos tenant={} usuario={}", tenantId, usuario.username());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (fecha != null) {
            return ResponseEntity.ok(turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha)));
        }
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        return ResponseEntity.ok(turnoRepository.findByTenantIdAndFechaBetweenOrderByFechaAscHoraAsc(
            tenantId, hoy.minusDays(7), hoy.plusDays(90)));
    }

    @GetMapping("/{tenantId}/mis-turnos")
    public ResponseEntity<List<Turno>> misTurnos(@PathVariable String tenantId,
                                                  @RequestParam(required = false) String fecha,
                                                  Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!tenantId.equals(usuario.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (usuario.barberoId() == null) return ResponseEntity.badRequest().build();
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        List<Turno> turnos = fecha != null
            ? turnoRepository.findByTenantIdAndBarberoIdAndFecha(tenantId, usuario.barberoId(), LocalDate.parse(fecha))
            : turnoRepository.findByTenantIdAndBarberoIdAndFechaBetweenOrderByFechaAscHoraAsc(
                tenantId, usuario.barberoId(), hoy.minusDays(7), hoy.plusDays(90));
        return ResponseEntity.ok(turnos);
    }

    @PostMapping("/{tenantId}")
    @Transactional
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public ResponseEntity<?> crear(@PathVariable String tenantId, @RequestBody TurnoCrearRequest request) {
        log.info("turno-crear tenant={} barbero={} servicio={} fecha={}", tenantId, request.barberoId(),
            request.servicio() != null ? request.servicio().replaceAll("[\r\n\t]", "_") : null, request.fecha());
        if (request.fecha() == null || request.fecha().isBefore(LocalDate.now(tenantClockService.clockFor(tenantId)))) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pueden crear reservas en fechas pasadas."));
        }
        Turno turno = new Turno();
        turno.setTenantId(tenantId);
        turno.setBarberoId(request.barberoId());
        turno.setServicio(request.servicio());
        turno.setFecha(request.fecha());
        turno.setHora(request.hora());
        turno.setPaciente(request.paciente());
        turno.setTelefono(request.telefono());
        turno.setEstado("RESERVADO");

        if (turno.getBarberoId() != null && turno.getServicio() != null && turno.getHora() != null) {
            if (barberoRepository.bloquearParaReserva(tenantId, turno.getBarberoId()).isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El barbero no está disponible para este negocio."));
            }
            int duracion = disponibilidadService.obtenerDuracion(tenantId, turno.getServicio());
            String horaFin = disponibilidadService.calcularHoraFin(turno.getHora(), duracion);
            turno.setHoraFin(horaFin);
            if (!disponibilidadService.validar(turno.getBarberoId(), turno.getFecha(), turno.getHora(), horaFin)) {
                log.warn("CONFLICT turno-crear tenant={} barbero={} fecha={} hora={}", tenantId, turno.getBarberoId(), turno.getFecha(), turno.getHora());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El horario ya no está disponible. Por favor elegí otro."));
            }
        }
        return ResponseEntity.ok(turnoRepository.save(turno));
    }

    @PutMapping("/{tenantId}/{id}/reasignar")
    @Transactional
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public ResponseEntity<?> reasignar(@PathVariable String tenantId,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Long> body,
                                        Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u) || !"ADMIN".equals(u.rol())) {
            log.warn("FORBIDDEN reasignar-turno tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Long nuevoBarberoId = body.get("barberoId");
        if (nuevoBarberoId == null) return ResponseEntity.badRequest().body(Map.of("error", "barberoId requerido"));

        return turnoRepository.findById(id)
            .filter(t -> t.getTenantId().equals(tenantId))
            .map(t -> {
                boolean barberoValido = barberoRepository.bloquearParaReserva(tenantId, nuevoBarberoId).isPresent();
                if (!barberoValido) {
                    return ResponseEntity.badRequest().<Object>body(Map.of("error", "Barbero no válido para este tenant"));
                }
                if (t.getHora() != null && t.getHoraFin() != null) {
                    boolean disponible = disponibilidadService.validar(nuevoBarberoId, t.getFecha(), t.getHora(), t.getHoraFin());
                    if (!disponible) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).<Object>body(
                            Map.of("error", "El barbero seleccionado no está disponible en ese horario"));
                    }
                }
                t.setBarberoId(nuevoBarberoId);
                return ResponseEntity.<Object>ok(turnoRepository.save(t));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public ResponseEntity<?> eliminar(@PathVariable String tenantId,
                                       @PathVariable Long id,
                                       Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !"ADMIN".equals(u.rol()) || !tenantId.equals(u.tenantId())) {
            log.warn("FORBIDDEN eliminar-turno tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return turnoRepository.findById(id)
            .filter(t -> t.getTenantId().equals(tenantId))
            .map(t -> { turnoRepository.delete(t); log.info("turno-eliminado tenant={} id={}", tenantId, id); return ResponseEntity.ok().build(); })
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}/{id}/estado")
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public ResponseEntity<Turno> actualizarEstado(@PathVariable String tenantId,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication auth) {
        return turnoRepository.findById(id)
            .filter(t -> {
                if (!t.getTenantId().equals(tenantId)) return false;
                if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) return false;
                if ("BARBERO".equals(u.rol())) {
                    return u.barberoId() != null && u.barberoId().equals(t.getBarberoId());
                }
                return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
            })
            .map(t -> {
                t.setEstado(body.get("estado"));
                return ResponseEntity.ok(turnoRepository.save(t));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
