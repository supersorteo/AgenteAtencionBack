package com.agente.atencion.controller;

import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/negocio")
public class NegocioController {

    @Autowired private TenantRepository tenantRepository;

    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> getTenant(@PathVariable String tenantId, Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return tenantRepository.findById(tenantId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{tenantId}/contexto")
    public ResponseEntity<Tenant> actualizarContexto(@PathVariable String tenantId,
                                                      @RequestBody Map<String, String> body,
                                                      Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String ctx = body.get("contexto");
        if (ctx == null || ctx.isBlank()) return ResponseEntity.badRequest().build();
        return tenantRepository.findById(tenantId).map(t -> {
            t.setContexto(ctx);
            return ResponseEntity.ok(tenantRepository.save(t));
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(Authentication auth, String tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) return false;
        return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
    }
}
