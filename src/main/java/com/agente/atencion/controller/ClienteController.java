package com.agente.atencion.controller;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.AuthUtils;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
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
    @Autowired private com.agente.atencion.service.DashboardService dashboardService;

    @GetMapping("/{tenantId}/resumen")
    public ResponseEntity<?> resumen(@PathVariable String tenantId, Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(dashboardService.clientes(tenantId));
    }

    static final int TAM_MAXIMO = 200;
    static final int TAM_POR_DEFECTO = 50;
    static final int HISTORIAL_MAXIMO = 200;

    @GetMapping("/{tenantId}")
    public ResponseEntity<?> listar(@PathVariable String tenantId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        int pagina = Math.max(page, 0);
        int tamano = size < 1 ? TAM_POR_DEFECTO : Math.min(size, TAM_MAXIMO);
        Slice<Object[]> filas = turnoRepository.buscarClientes(tenantId, patron(q), PageRequest.of(pagina, tamano));

        List<Map<String, Object>> clientes = filas.getContent()
            .stream()
            .map(row -> Map.<String, Object>of(
                "nombre",       row[0] != null ? row[0] : "",
                "telefono",     row[1] != null ? row[1] : "",
                "totalVisitas", row[2],
                "ultimaVisita", row[3] != null ? row[3].toString() : ""
            ))
            .toList();

        return ResponseEntity.ok()
            .header("X-Has-More", String.valueOf(filas.hasNext()))
            .header("Access-Control-Expose-Headers", "X-Has-More")
            .body(clientes);
    }

    static String patron(String q) {
        if (q == null || q.isBlank()) return "%";
        String limpio = q.trim().toLowerCase()
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + limpio + "%";
    }

    @GetMapping("/{tenantId}/{nombre}/historial")
    public ResponseEntity<?> historial(@PathVariable String tenantId,
                                        @PathVariable String nombre,
                                        Authentication auth) {
        if (!AuthUtils.isAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        List<Turno> turnos = turnoRepository.findByTenantIdAndPacienteOrderByFechaDescHoraDesc(
            tenantId, nombre, PageRequest.of(0, HISTORIAL_MAXIMO));
        return ResponseEntity.ok(turnos);
    }
}
