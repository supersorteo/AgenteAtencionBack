package com.agente.atencion.controller;

import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.security.AuthUtils;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.DisponibilidadCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/servicios")
public class ServicioController {

    @Autowired private ServicioRepository servicioRepository;
    @Autowired private DisponibilidadCache disponibilidadCache;

    @GetMapping("/{tenantId}")
    public List<Servicio> listar(@PathVariable String tenantId) {
        return servicioRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping("/{tenantId}")
    @CacheEvict(value = "servicios", key = "#tenantId")
    public ResponseEntity<Servicio> crear(@PathVariable String tenantId,
                                           @RequestBody Servicio servicio,
                                           Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        servicio.setId(null);
        servicio.setTenantId(tenantId);
        servicio.setActivo(true);
        return ResponseEntity.ok(servicioRepository.save(servicio));
    }

    @PutMapping("/{tenantId}/{id}")
    @CacheEvict(value = "servicios", key = "#tenantId")
    public ResponseEntity<Servicio> actualizar(@PathVariable String tenantId,
                                                @PathVariable Long id,
                                                @RequestBody Servicio datos,
                                                Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return servicioRepository.findById(id)
            .filter(s -> s.getTenantId().equals(tenantId))
            .map(s -> {
                s.setNombre(datos.getNombre());
                s.setDescripcion(datos.getDescripcion());
                s.setPrecio(datos.getPrecio());
                s.setDuracionMinutos(datos.getDuracionMinutos());
                s.setEmoji(datos.getEmoji());
                Servicio guardado = servicioRepository.save(s);
                disponibilidadCache.evictTenant(tenantId);
                return ResponseEntity.ok(guardado);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    @CacheEvict(value = "servicios", key = "#tenantId")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId,
                                          @PathVariable Long id,
                                          Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return servicioRepository.findById(id)
            .filter(s -> s.getTenantId().equals(tenantId))
            .map(s -> {
                s.setActivo(false);
                servicioRepository.save(s);
                disponibilidadCache.evictTenant(tenantId);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
