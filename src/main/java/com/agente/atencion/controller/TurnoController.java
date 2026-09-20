package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping("/{tenantId}")
    public List<Turno> listar(@PathVariable String tenantId,
                               @RequestParam(required = false) String fecha) {
        if (fecha != null) {
            return turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha));
        }
        return turnoRepository.findByTenantIdOrderByFechaAscHoraAsc(tenantId);
    }

    @PostMapping("/{tenantId}")
    public Turno crear(@PathVariable String tenantId, @RequestBody Turno turno) {
        turno.setTenantId(tenantId);
        return turnoRepository.save(turno);
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
