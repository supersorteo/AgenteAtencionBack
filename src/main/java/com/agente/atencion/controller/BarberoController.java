package com.agente.atencion.controller;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.repository.*;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/barberos")
public class BarberoController {

    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @GetMapping("/{tenantId}")
    public List<Barbero> listar(@PathVariable String tenantId) {
        return barberoRepository.findByTenantIdAndActivoTrue(tenantId);
    }

    @PostMapping("/{tenantId}")
    @Transactional
    public ResponseEntity<Barbero> crear(@PathVariable String tenantId, @RequestBody Barbero barbero,
                                          Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        barbero.setTenantId(tenantId);
        Barbero saved = barberoRepository.save(barbero);
        for (int dia = 2; dia <= 7; dia++) {
            HorarioBarbero h = new HorarioBarbero();
            h.setBarberoId(saved.getId());
            h.setDiaSemana(dia);
            h.setHoraInicio("09:00");
            h.setHoraFin("18:00");
            horarioRepository.save(h);
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{tenantId}/{id}")
    public ResponseEntity<Barbero> actualizar(@PathVariable String tenantId,
                                               @PathVariable Long id,
                                               @RequestBody Barbero datos,
                                               Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
    @Transactional
    public ResponseEntity<Void> eliminar(@PathVariable String tenantId, @PathVariable Long id,
                                          Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return barberoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .map(b -> {
                turnoRepository.deleteByBarberoId(id);
                bloqueoRepository.deleteByBarberoId(id);
                horarioRepository.deleteByBarberoId(id);
                usuarioRepository.findByBarberoId(id).ifPresent(usuarioRepository::delete);
                barberoRepository.delete(b);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // Barbero actualiza su propio perfil (solo foto y especialidad)
    @PatchMapping("/{tenantId}/mi-perfil")
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
    public List<HorarioBarbero> getHorario(@PathVariable Long id) {
        return horarioRepository.findByBarberoId(id);
    }

    @PostMapping("/{tenantId}/{id}/horario")
    @Transactional
    public ResponseEntity<HorarioBarbero> setHorarioDia(@PathVariable String tenantId,
                                                          @PathVariable Long id,
                                                          @RequestBody HorarioBarbero horario,
                                                          Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (barberoRepository.findById(id).filter(b -> b.getTenantId().equals(tenantId)).isEmpty())
            return ResponseEntity.notFound().build();
        horario.setBarberoId(id);
        horarioRepository.findByBarberoIdAndDiaSemana(id, horario.getDiaSemana())
            .ifPresent(h -> horario.setId(h.getId()));
        return ResponseEntity.ok(horarioRepository.save(horario));
    }

    @DeleteMapping("/{tenantId}/{id}/horario/{dia}")
    @Transactional
    public ResponseEntity<Void> eliminarDia(@PathVariable String tenantId,
                                             @PathVariable Long id,
                                             @PathVariable Integer dia,
                                             Authentication auth) {
        if (!isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (barberoRepository.findById(id).filter(b -> b.getTenantId().equals(tenantId)).isEmpty())
            return ResponseEntity.notFound().build();
        horarioRepository.deleteByBarberoIdAndDiaSemana(id, dia);
        return ResponseEntity.ok().build();
    }

    private boolean isAdmin(Authentication auth, String tenantId) {
        return auth != null
            && auth.getPrincipal() instanceof UsuarioAutenticado u
            && "ADMIN".equals(u.rol())
            && tenantId.equals(u.tenantId());
    }
}
