package com.agente.atencion.controller;

import com.agente.atencion.entity.CategoriaServicio;
import com.agente.atencion.repository.CategoriaServicioRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaServicioController {

    @Autowired private CategoriaServicioRepository repo;

    @GetMapping("/{tenantId}")
    public List<CategoriaServicio> listar(@PathVariable String tenantId) {
        return repo.findByTenantIdAndActivoTrueOrderByOrdenAsc(tenantId);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<CategoriaServicio> crear(@PathVariable String tenantId,
                                                    @RequestBody CategoriaServicio cat,
                                                    Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        cat.setTenantId(tenantId);
        cat.setActivo(true);
        return ResponseEntity.ok(repo.save(cat));
    }

    @PutMapping("/{tenantId}/{id}")
    public ResponseEntity<CategoriaServicio> actualizar(@PathVariable String tenantId,
                                                         @PathVariable Long id,
                                                         @RequestBody CategoriaServicio datos,
                                                         Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return repo.findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .map(c -> {
                c.setNombre(datos.getNombre());
                c.setEmoji(datos.getEmoji());
                c.setOrden(datos.getOrden());
                return ResponseEntity.ok(repo.save(c));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId,
                                          @PathVariable Long id,
                                          Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return repo.findById(id)
            .filter(c -> c.getTenantId().equals(tenantId))
            .map(c -> {
                c.setActivo(false);
                repo.save(c);
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
