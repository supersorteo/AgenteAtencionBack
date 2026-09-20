package com.agente.atencion.controller;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.HorarioBarberoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/barberos")
@CrossOrigin(origins = "*")
public class BarberoController {

    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;

    @GetMapping("/{tenantId}")
    public List<Barbero> listar(@PathVariable String tenantId) {
        return barberoRepository.findByTenantId(tenantId);
    }

    @PostMapping("/{tenantId}")
    public Barbero crear(@PathVariable String tenantId, @RequestBody Barbero barbero) {
        barbero.setTenantId(tenantId);
        return barberoRepository.save(barbero);
    }

    @PutMapping("/{tenantId}/{id}")
    public ResponseEntity<Barbero> actualizar(@PathVariable String tenantId,
                                               @PathVariable Long id,
                                               @RequestBody Barbero datos) {
        return barberoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                b.setNombre(datos.getNombre());
                b.setEspecialidad(datos.getEspecialidad());
                b.setFoto(datos.getFoto());
                b.setActivo(datos.getActivo());
                return ResponseEntity.ok(barberoRepository.save(b));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable String tenantId, @PathVariable Long id) {
        return barberoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                b.setActivo(false);
                barberoRepository.save(b);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // Horario semanal del barbero
    @GetMapping("/{tenantId}/{id}/horario")
    public List<HorarioBarbero> getHorario(@PathVariable Long id) {
        return horarioRepository.findByBarberoId(id);
    }

    @PostMapping("/{tenantId}/{id}/horario")
    @Transactional
    public HorarioBarbero setHorarioDia(@PathVariable Long id,
                                         @RequestBody HorarioBarbero horario) {
        horario.setBarberoId(id);
        // Reemplaza si ya existe para ese día
        horarioRepository.findByBarberoIdAndDiaSemana(id, horario.getDiaSemana())
            .ifPresent(h -> horario.setId(h.getId()));
        return horarioRepository.save(horario);
    }

    @DeleteMapping("/{tenantId}/{id}/horario/{dia}")
    @Transactional
    public ResponseEntity<Void> eliminarDia(@PathVariable Long id, @PathVariable Integer dia) {
        horarioRepository.deleteByBarberoIdAndDiaSemana(id, dia);
        return ResponseEntity.ok().build();
    }
}
