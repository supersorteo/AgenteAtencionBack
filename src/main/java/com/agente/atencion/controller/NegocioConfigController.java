package com.agente.atencion.controller;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.TenantClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class NegocioConfigController {

    @Autowired private NegocioConfigRepository repo;
    @Autowired private TenantRepository tenantRepo;
    @Autowired private TenantClockService tenantClockService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> get(@PathVariable String tenantId) {
        return tenantRepo.findById(tenantId)
            .map(tenant -> {
                if (!tenant.isActivo()) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body((Object) Map.of("error", "Barbería desactivada", "code", "TENANT_INACTIVE"));
                }
                return repo.findById(tenantId)
                    .map(c -> ResponseEntity.ok((Object) c))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<NegocioConfig> save(@PathVariable String tenantId,
                                               @RequestBody NegocioConfig config,
                                               Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !tenantId.equals(u.tenantId())
                || !"ADMIN".equals(u.rol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        config.setTenantId(tenantId);
        NegocioConfig saved = repo.save(config);
        tenantClockService.invalidate(tenantId);
        return ResponseEntity.ok(saved);
    }
}
