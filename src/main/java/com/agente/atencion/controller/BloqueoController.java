package com.agente.atencion.controller;

import com.agente.atencion.entity.BloqueoHorario;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.BloqueoHorarioRepository;
import com.agente.atencion.security.AuthUtils;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.DisponibilidadCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bloqueos")
public class BloqueoController {

    private static final Logger log = LoggerFactory.getLogger(BloqueoController.class);
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadCache disponibilidadCache;

    @GetMapping("/{tenantId}")
    public ResponseEntity<List<BloqueoHorario>> listar(@PathVariable String tenantId,
                                                        @RequestParam(required = false) Long barberoId,
                                                        Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (barberoId != null) {
            return ResponseEntity.ok(bloqueoRepository.findByTenantIdAndBarberoId(tenantId, barberoId));
        }
        return ResponseEntity.ok(bloqueoRepository.findByTenantId(tenantId));
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<?> crear(@PathVariable String tenantId,
                                    @RequestBody BloqueoHorario bloqueo,
                                    Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) {
            log.warn("FORBIDDEN crear-bloqueo tenant={}", tenantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (bloqueo.getBarberoId() == null || barberoRepository.findById(bloqueo.getBarberoId())
                .filter(b -> tenantId.equals(b.getTenantId())).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        bloqueo.setId(null);
        bloqueo.setTenantId(tenantId);
        BloqueoHorario saved = bloqueoRepository.save(bloqueo);
        disponibilidadCache.evictTenant(tenantId);
        log.info("bloqueo-creado tenant={} barbero={} fecha={}", tenantId, saved.getBarberoId(), saved.getFecha());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId,
                                          @PathVariable Long id,
                                          Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) {
            log.warn("FORBIDDEN eliminar-bloqueo tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return bloqueoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                bloqueoRepository.delete(b);
                disponibilidadCache.evictTenant(tenantId);
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
        bloqueo.setId(null);
        bloqueo.setTenantId(u.tenantId()); // siempre del JWT, nunca del body ni del path
        bloqueo.setBarberoId(u.barberoId());
        BloqueoHorario guardado = bloqueoRepository.save(bloqueo);
        disponibilidadCache.evictTenant(tenantId);
        return ResponseEntity.ok(guardado);
    }

    @DeleteMapping("/{tenantId}/mios/{id}")
    public ResponseEntity<Void> eliminarMio(@PathVariable String tenantId,
                                             @PathVariable Long id,
                                             Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return bloqueoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId) && b.getBarberoId().equals(u.barberoId()))
            .map(b -> { bloqueoRepository.delete(b); disponibilidadCache.evictTenant(tenantId); return ResponseEntity.ok().<Void>build(); })
            .orElse(ResponseEntity.notFound().build());
    }
}
