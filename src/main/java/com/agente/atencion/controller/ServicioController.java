package com.agente.atencion.controller;

import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/servicios")
public class ServicioController {

    @Autowired private ServicioRepository servicioRepository;

    @GetMapping("/{tenantId}")
    public List<Servicio> listar(@PathVariable String tenantId) {
        return servicioRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping("/{tenantId}")
    public Servicio crear(@PathVariable String tenantId, @RequestBody Servicio servicio) {
        servicio.setTenantId(tenantId);
        servicio.setActivo(true);
        return servicioRepository.save(servicio);
    }

    @PutMapping("/{tenantId}/{id}")
    public ResponseEntity<Servicio> actualizar(@PathVariable String tenantId,
                                                @PathVariable Long id,
                                                @RequestBody Servicio datos) {
        return servicioRepository.findById(id)
            .filter(s -> s.getTenantId().equals(tenantId))
            .map(s -> {
                s.setNombre(datos.getNombre());
                s.setDescripcion(datos.getDescripcion());
                s.setPrecio(datos.getPrecio());
                s.setDuracionMinutos(datos.getDuracionMinutos());
                s.setEmoji(datos.getEmoji());
                return ResponseEntity.ok(servicioRepository.save(s));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId, @PathVariable Long id) {
        return servicioRepository.findById(id)
            .filter(s -> s.getTenantId().equals(tenantId))
            .map(s -> {
                s.setActivo(false);
                servicioRepository.save(s);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
