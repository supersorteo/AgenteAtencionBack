package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    @Autowired private TurnoRepository turnoRepository;

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> listar(@PathVariable String tenantId, Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        List<Map<String, Object>> clientes = turnoRepository.findClientesSummary(tenantId)
            .stream()
            .map(row -> Map.<String, Object>of(
                "nombre",       row[0] != null ? row[0] : "",
                "telefono",     row[1] != null ? row[1] : "",
                "totalVisitas", row[2],
                "ultimaVisita", row[3] != null ? row[3].toString() : ""
            ))
            .toList();

        return ResponseEntity.ok(clientes);
    }

    @GetMapping("/{tenantId}/{nombre}/historial")
    public ResponseEntity<?> historial(@PathVariable String tenantId,
                                        @PathVariable String nombre,
                                        Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        List<Turno> turnos = turnoRepository.findByTenantIdAndPacienteOrderByFechaDescHoraDesc(tenantId, nombre);
        return ResponseEntity.ok(turnos);
    }

    private boolean esAdmin(Authentication auth, String tenantId) {
        return auth != null
            && auth.getPrincipal() instanceof UsuarioAutenticado u
            && "ADMIN".equals(u.rol())
            && tenantId.equals(u.tenantId());
    }
}
