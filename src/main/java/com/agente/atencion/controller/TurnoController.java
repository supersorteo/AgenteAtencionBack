package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.service.DisponibilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/turnos")
@CrossOrigin(origins = "*")
public class TurnoController {

    @Autowired private TurnoRepository turnoRepository;
    @Autowired private DisponibilidadService disponibilidadService;

    @GetMapping("/{tenantId}")
    public List<Turno> listar(@PathVariable String tenantId,
                               @RequestParam(required = false) String fecha) {
        if (fecha != null) {
            return turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha));
        }
        return turnoRepository.findByTenantIdOrderByFechaAscHoraAsc(tenantId);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<?> crear(@PathVariable String tenantId, @RequestBody Turno turno) {
        turno.setTenantId(tenantId);
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

    @PutMapping("/{tenantId}/{id}/estado")
    public ResponseEntity<Turno> actualizarEstado(@PathVariable String tenantId,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
        return turnoRepository.findById(id)
            .filter(t -> t.getTenantId().equals(tenantId))
            .map(t -> {
                t.setEstado(body.get("estado"));
                return ResponseEntity.ok(turnoRepository.save(t));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
