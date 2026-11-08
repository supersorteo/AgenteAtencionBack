package com.agente.atencion.controller;

import com.agente.atencion.dto.TurnoCrearRequest;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.service.TenantClockService;
import com.agente.atencion.service.TurnoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/turnos")
public class TurnoController {

    private static final Logger log = LoggerFactory.getLogger(TurnoController.class);
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private TenantClockService tenantClockService;
    @Autowired private TurnoService turnoService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<List<Turno>> listar(@PathVariable String tenantId,
                               @RequestParam(required = false) String fecha, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ADMIN".equals(usuario.rol()) || !tenantId.equals(usuario.tenantId())) {
            log.warn("FORBIDDEN listar-turnos tenant={} usuario={}", tenantId, usuario.username());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (fecha != null) {
            return ResponseEntity.ok(turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha)));
        }
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        return ResponseEntity.ok(turnoRepository.findByTenantIdAndFechaBetweenOrderByFechaAscHoraAsc(
            tenantId, hoy.minusDays(7), hoy.plusDays(90)));
    }

    @GetMapping("/{tenantId}/mis-turnos")
    public ResponseEntity<List<Turno>> misTurnos(@PathVariable String tenantId,
                                                  @RequestParam(required = false) String fecha,
                                                  Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!tenantId.equals(usuario.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (usuario.barberoId() == null) return ResponseEntity.badRequest().build();
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(tenantId));
        List<Turno> turnos = fecha != null
            ? turnoRepository.findByTenantIdAndBarberoIdAndFecha(tenantId, usuario.barberoId(), LocalDate.parse(fecha))
            : turnoRepository.findByTenantIdAndBarberoIdAndFechaBetweenOrderByFechaAscHoraAsc(
                tenantId, usuario.barberoId(), hoy.minusDays(7), hoy.plusDays(90));
        return ResponseEntity.ok(turnos);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<?> crear(@PathVariable String tenantId, @RequestBody TurnoCrearRequest request) {
        log.info("turno-crear tenant={} barbero={} servicio={} fecha={}", tenantId, request.barberoId(),
            request.servicio() != null ? request.servicio().replaceAll("[\r\n\t]", "_") : null, request.fecha());
        return ResponseEntity.ok(turnoService.crear(tenantId, request));
    }

    @PutMapping("/{tenantId}/{id}/reasignar")
    public ResponseEntity<?> reasignar(@PathVariable String tenantId,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Long> body,
                                        Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u) || !"ADMIN".equals(u.rol())) {
            log.warn("FORBIDDEN reasignar-turno tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!tenantId.equals(u.tenantId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Long nuevoBarberoId = body.get("barberoId");
        if (nuevoBarberoId == null) return ResponseEntity.badRequest().body(Map.of("error", "barberoId requerido"));

        return ResponseEntity.ok(turnoService.reasignar(tenantId, id, nuevoBarberoId));
    }

    @DeleteMapping("/{tenantId}/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String tenantId,
                                       @PathVariable Long id,
                                       Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)
                || !"ADMIN".equals(u.rol()) || !tenantId.equals(u.tenantId())) {
            log.warn("FORBIDDEN eliminar-turno tenant={} id={}", tenantId, id);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean eliminado = turnoService.eliminar(tenantId, id);
        if (!eliminado) return ResponseEntity.notFound().build();
        log.info("turno-eliminado tenant={} id={}", tenantId, id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tenantId}/{id}/estado")
    public ResponseEntity<Turno> actualizarEstado(@PathVariable String tenantId,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return turnoService.actualizarEstado(tenantId, id, body.get("estado"), u)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
