package com.agente.atencion.controller;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.TenantClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/config")
public class NegocioConfigController {

    @Autowired private NegocioConfigRepository repo;
    @Autowired private TenantClockService tenantClockService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<NegocioConfig> get(@PathVariable String tenantId) {
        return repo.findById(tenantId)
            .map(ResponseEntity::ok)
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
