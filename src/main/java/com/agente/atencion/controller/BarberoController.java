package com.agente.atencion.controller;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.repository.*;
import com.agente.atencion.security.AuthUtils;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.BarberoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/barberos")
public class BarberoController {

    private static final Logger log = LoggerFactory.getLogger(BarberoController.class);
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BarberoService barberoService;

    @GetMapping("/{tenantId}")
    public List<Barbero> listar(@PathVariable String tenantId) {
        return barberoRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<Barbero> crear(@PathVariable String tenantId, @RequestBody Barbero barbero,
                                          Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) {
            log.warn("FORBIDDEN crear-barbero tenant={}", tenantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Barbero saved = barberoService.crearConHorarios(tenantId, barbero);
        log.info("barbero-creado tenant={} id={} nombre={}", tenantId, saved.getId(), saved.getNombre());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{tenantId}/{id}")
    @CacheEvict(value = "barberos", key = "#tenantId")
    public ResponseEntity<Barbero> actualizar(@PathVariable String tenantId,
                                               @PathVariable Long id,
                                               @RequestBody Barbero datos,
                                               Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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

    @GetMapping("/{tenantId}/{id}/impacto")
    public ResponseEntity<Map<String, Object>> impacto(@PathVariable String tenantId,
                                                        @PathVariable Long id,
                                                        Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return barberoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                long turnos = turnoRepository.countByBarberoId(id);
                boolean tieneUsuario = usuarioRepository.findByBarberoId(id).isPresent();
                return ResponseEntity.ok(Map.<String, Object>of(
                    "nombre", b.getNombre(),
                    "turnos", turnos,
                    "tieneUsuario", tieneUsuario
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId, @PathVariable Long id,
                                          Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) {
            log.warn("FORBIDDEN eliminar-barbero tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (barberoRepository.findById(id).filter(b -> b.getTenantId().equals(tenantId)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.info("barbero-eliminado tenant={} id={}", tenantId, id);
        barberoService.eliminarConCascade(tenantId, id);
        return ResponseEntity.ok().build();
    }

    // Barbero actualiza su propio perfil (solo foto y especialidad)
    @PatchMapping("/{tenantId}/mi-perfil")
    @CacheEvict(value = "barberos", key = "#tenantId")
    public ResponseEntity<Barbero> actualizarMiPerfil(@PathVariable String tenantId,
                                                       @RequestBody Barbero datos,
                                                       Authentication auth) {
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado u) || u.barberoId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return barberoRepository.findById(u.barberoId())
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                if (datos.getEspecialidad() != null) b.setEspecialidad(datos.getEspecialidad());
                if (datos.getFoto() != null) b.setFoto(datos.getFoto());
                return ResponseEntity.ok(barberoRepository.save(b));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // Horario semanal del barbero
    @GetMapping("/{tenantId}/{id}/horario")
    public ResponseEntity<List<HorarioBarbero>> getHorario(@PathVariable String tenantId, @PathVariable Long id) {
        if (barberoRepository.findById(id).filter(b -> tenantId.equals(b.getTenantId())).isEmpty())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(horarioRepository.findByBarberoId(id));
    }

    @PostMapping("/{tenantId}/{id}/horario")
    public ResponseEntity<HorarioBarbero> setHorarioDia(@PathVariable String tenantId,
                                                          @PathVariable Long id,
                                                          @RequestBody HorarioBarbero horario,
                                                          Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (barberoRepository.findById(id).filter(b -> b.getTenantId().equals(tenantId)).isEmpty())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(barberoService.guardarHorarioDia(id, horario));
    }

    @DeleteMapping("/{tenantId}/{id}/horario/{dia}")
    public ResponseEntity<Void> eliminarDia(@PathVariable String tenantId,
                                             @PathVariable Long id,
                                             @PathVariable Integer dia,
                                             Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (barberoRepository.findById(id).filter(b -> b.getTenantId().equals(tenantId)).isEmpty())
            return ResponseEntity.notFound().build();
        barberoService.eliminarHorarioDia(id, dia);
        return ResponseEntity.ok().build();
    }
}
