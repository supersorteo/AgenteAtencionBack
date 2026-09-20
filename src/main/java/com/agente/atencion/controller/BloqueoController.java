package com.agente.atencion.controller;

import com.agente.atencion.entity.BloqueoHorario;
import com.agente.atencion.repository.BloqueoHorarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bloqueos")
@CrossOrigin(origins = "*")
public class BloqueoController {

    @Autowired private BloqueoHorarioRepository bloqueoRepository;

    @GetMapping("/{tenantId}")
    public List<BloqueoHorario> listar(@PathVariable String tenantId,
                                        @RequestParam(required = false) Long barberoId) {
        if (barberoId != null) {
            return bloqueoRepository.findAll().stream()
                .filter(b -> b.getTenantId().equals(tenantId) && b.getBarberoId().equals(barberoId))
                .toList();
        }
        return bloqueoRepository.findByTenantId(tenantId);
    }

    @PostMapping("/{tenantId}")
    public BloqueoHorario crear(@PathVariable String tenantId, @RequestBody BloqueoHorario bloqueo) {
        bloqueo.setTenantId(tenantId);
        return bloqueoRepository.save(bloqueo);
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId, @PathVariable Long id) {
        return bloqueoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                bloqueoRepository.delete(b);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
