package com.agente.atencion.controller;

import com.agente.atencion.dto.OnboardingStatusDTO;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private NegocioConfigRepository negocioConfigRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private BarberoRepository barberoRepository;

    @GetMapping("/{tenantId}")
    public ResponseEntity<OnboardingStatusDTO> getStatus(@PathVariable String tenantId, Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return tenantRepository.findById(tenantId).map(tenant -> {
            boolean configCompleta = negocioConfigRepository.existsById(tenantId);
            boolean tieneServicios = !servicioRepository.findByTenantIdAndActivoTrue(tenantId).isEmpty();
            boolean tieneBarberos = !barberoRepository.findByTenantIdAndActivoTrue(tenantId).isEmpty();
            return ResponseEntity.ok(new OnboardingStatusDTO(
                Boolean.TRUE.equals(tenant.getTourVisto()), configCompleta, tieneServicios, tieneBarberos
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{tenantId}/tour-visto")
    public ResponseEntity<Void> marcarTourVisto(@PathVariable String tenantId, Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return tenantRepository.findById(tenantId).map(tenant -> {
            tenant.setTourVisto(true);
            tenantRepository.save(tenant);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(Authentication auth, String tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) return false;
        return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
    }
}
