package com.agente.atencion.controller;

import com.agente.atencion.entity.BloqueoHorario;
import com.agente.atencion.repository.BloqueoHorarioRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bloqueos")
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

    // ── Endpoints para barbero: solo gestiona SUS propios bloqueos ──────────

    @GetMapping("/{tenantId}/mios")
    public ResponseEntity<List<BloqueoHorario>> misBl(@PathVariable String tenantId, Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(bloqueoRepository.findByTenantIdAndBarberoId(tenantId, u.barberoId()));
    }

    @PostMapping("/{tenantId}/mios")
    public ResponseEntity<BloqueoHorario> crearMio(@PathVariable String tenantId,
                                                    @RequestBody BloqueoHorario bloqueo,
                                                    Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u) || u.barberoId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        bloqueo.setTenantId(u.tenantId()); // siempre del JWT, nunca del body ni del path
        bloqueo.setBarberoId(u.barberoId());
        return ResponseEntity.ok(bloqueoRepository.save(bloqueo));
    }

    @DeleteMapping("/{tenantId}/mios/{id}")
    public ResponseEntity<Void> eliminarMio(@PathVariable String tenantId,
                                             @PathVariable Long id,
                                             Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return bloqueoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId) && b.getBarberoId().equals(u.barberoId()))
            .map(b -> { bloqueoRepository.delete(b); return ResponseEntity.ok().<Void>build(); })
            .orElse(ResponseEntity.notFound().build());
    }
}
