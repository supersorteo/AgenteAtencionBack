package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.DisponibilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadService disponibilidadService;

    @GetMapping("/{tenantId}")
    public List<Turno> listar(@PathVariable String tenantId,
                               @RequestParam(required = false) String fecha) {
        if (fecha != null) {
            return turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha));
        }
        return turnoRepository.findByTenantIdOrderByFechaAscHoraAsc(tenantId);
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
        List<Turno> turnos = fecha != null
            ? turnoRepository.findByTenantIdAndBarberoIdAndFecha(tenantId, usuario.barberoId(), LocalDate.parse(fecha))
            : turnoRepository.findByTenantIdAndBarberoIdOrderByFechaAscHoraAsc(tenantId, usuario.barberoId());
        return ResponseEntity.ok(turnos);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<?> crear(@PathVariable String tenantId, @RequestBody Turno turno) {
        turno.setTenantId(tenantId);
        if (turno.getFecha() == null || turno.getFecha().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se pueden crear reservas en fechas pasadas."));
        }
        // Calcular horaFin si tenemos barberoId y servicio
        if (turno.getBarberoId() != null && turno.getServicio() != null && turno.getHora() != null) {
            int duracion = disponibilidadService.obtenerDuracion(tenantId, turno.getServicio());
            String horaFin = disponibilidadService.calcularHoraFin(turno.getHora(), duracion);
            turno.setHoraFin(horaFin);
            // Validar que el slot sigue disponible (protección contra doble booking)
            if (!disponibilidadService.validar(turno.getBarberoId(), turno.getFecha(), turno.getHora(), horaFin)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "El horario ya no está disponible. Por favor elegí otro."));
            }
        }
        return ResponseEntity.ok(turnoRepository.save(turno));
    }

    @PutMapping("/{tenantId}/{id}/reasignar")
    public ResponseEntity<?> reasignar(@PathVariable String tenantId,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Long> body,
                                        Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u) || !"ADMIN".equals(u.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Long nuevoBarberoId = body.get("barberoId");
        if (nuevoBarberoId == null) return ResponseEntity.badRequest().body(Map.of("error", "barberoId requerido"));

        return turnoRepository.findById(id)
            .filter(t -> t.getTenantId().equals(tenantId))
            .map(t -> {
                // Validar que el nuevo barbero existe y pertenece al tenant
                boolean barberoValido = barberoRepository.findById(nuevoBarberoId)
                    .filter(b -> b.getTenantId().equals(tenantId) && Boolean.TRUE.equals(b.getActivo()))
                    .isPresent();
                if (!barberoValido) {
                    return ResponseEntity.badRequest().<Object>body(Map.of("error", "Barbero no válido para este tenant"));
                }
                // Validar disponibilidad del nuevo barbero (si el turno tiene hora y horaFin)
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

    @PutMapping("/{tenantId}/{id}/estado")
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
