package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class UsuarioAdminController {

    private final UsuarioRepository usuarioRepo;
    private final BarberoRepository barberoRepo;
    private final PasswordEncoder encoder;

    public UsuarioAdminController(UsuarioRepository usuarioRepo, BarberoRepository barberoRepo, PasswordEncoder encoder) {
        this.usuarioRepo = usuarioRepo;
        this.barberoRepo = barberoRepo;
        this.encoder = encoder;
    }

    @GetMapping("/{tenantId}/usuarios")
    public ResponseEntity<?> listar(@PathVariable String tenantId, Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        List<Map<String, Object>> result = usuarioRepo.findByTenantIdAndActivoTrue(tenantId).stream()
            .map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "username", u.getUsername(),
                "rol", u.getRol(),
                "barberoId", u.getBarberoId() != null ? u.getBarberoId() : 0L,
                "activo", u.isActivo()
            ))
            .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{tenantId}/usuarios")
    public ResponseEntity<?> crear(@PathVariable String tenantId,
                                   @RequestBody Map<String, Object> body,
                                   Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        Long barberoId = body.get("barberoId") != null ? Long.valueOf(body.get("barberoId").toString()) : null;

        if (username == null || username.isBlank() || password == null || password.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "username y password son obligatorios"));
        if (usuarioRepo.findByUsernameAndActivoTrue(username).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "El usuario '" + username + "' ya existe"));
        if (barberoId != null && !barberoRepo.existsById(barberoId))
            return ResponseEntity.badRequest().body(Map.of("error", "Barbero no encontrado"));

        Usuario u = new Usuario();
        u.setUsername(username.toLowerCase().trim());
        u.setPassword(encoder.encode(password));
        u.setTenantId(tenantId);
        u.setRol("BARBERO");
        u.setBarberoId(barberoId);
        u.setActivo(true);
        Usuario saved = usuarioRepo.save(u);

        return ResponseEntity.ok(Map.of(
            "id", saved.getId(),
            "username", saved.getUsername(),
            "rol", saved.getRol(),
            "barberoId", saved.getBarberoId() != null ? saved.getBarberoId() : 0L,
            "password", password
        ));
    }

    @PatchMapping("/{tenantId}/usuarios/{id}/password")
    public ResponseEntity<?> resetearPassword(@PathVariable String tenantId,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, String> body,
                                               Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "La nueva contraseña no puede estar vacía"));

        return usuarioRepo.findById(id)
            .filter(u -> u.getTenantId().equals(tenantId))
            .map(u -> {
                u.setPassword(encoder.encode(newPassword));
                usuarioRepo.save(u);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "username", u.getUsername(),
                    "password", newPassword
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tenantId}/usuarios/{id}")
    public ResponseEntity<?> eliminar(@PathVariable String tenantId,
                                       @PathVariable Long id,
                                       Authentication auth) {
        if (!esAdmin(auth, tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return usuarioRepo.findById(id)
            .filter(u -> u.getTenantId().equals(tenantId) && "BARBERO".equals(u.getRol()))
            .map(u -> {
                u.setActivo(false);
                usuarioRepo.save(u);
                return ResponseEntity.ok().build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private boolean esAdmin(Authentication auth, String tenantId) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado u)) return false;
        return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
    }
}
