package com.agente.atencion.controller;

import com.agente.atencion.entity.Galeria;
import com.agente.atencion.repository.GaleriaRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/galeria")
public class GaleriaController {

    @Autowired private GaleriaRepository galeriaRepository;

    @GetMapping("/{tenantId}")
    public List<Galeria> listar(@PathVariable String tenantId) {
        return galeriaRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<Galeria> crear(@PathVariable String tenantId,
                                          @RequestBody Galeria galeria,
                                          Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        galeria.setTenantId(tenantId);
        return ResponseEntity.ok(galeriaRepository.save(galeria));
    }

    @PutMapping("/{tenantId}/{id}")
    public ResponseEntity<Galeria> actualizar(@PathVariable String tenantId,
                                               @PathVariable Long id,
                                               @RequestBody Galeria datos,
                                               Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return galeriaRepository.findById(id)
            .filter(g -> g.getTenantId().equals(tenantId))
            .map(g -> {
                g.setTitulo(datos.getTitulo());
                g.setDescripcion(datos.getDescripcion());
                g.setImagenUrl(datos.getImagenUrl());
                g.setCategoria(datos.getCategoria());
                g.setServicioId(datos.getServicioId());
                g.setActivo(datos.getActivo());
                return ResponseEntity.ok(galeriaRepository.save(g));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> ocultar(@PathVariable String tenantId,
                                         @PathVariable Long id,
                                         Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return galeriaRepository.findById(id)
            .filter(g -> g.getTenantId().equals(tenantId))
            .map(g -> {
                g.setActivo(false);
                galeriaRepository.save(g);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private boolean isAdmin(Authentication auth, String tenantId) {
        return auth != null
            && auth.getPrincipal() instanceof UsuarioAutenticado u
            && "ADMIN".equals(u.rol())
            && tenantId.equals(u.tenantId());
    }
}
