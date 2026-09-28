package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import com.agente.atencion.security.JwtUtil;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository repo;
    private final TenantRepository tenantRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;

    public AuthController(UsuarioRepository repo, TenantRepository tenantRepo,
                          JwtUtil jwtUtil, PasswordEncoder encoder) {
        this.repo = repo;
        this.tenantRepo = tenantRepo;
        this.jwtUtil = jwtUtil;
        this.encoder = encoder;
    }

    @PatchMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(@RequestBody Map<String, String> body, Authentication auth) {
        if (!(auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String actual = body.get("actual");
        String nueva  = body.get("nueva");
        if (actual == null || nueva == null || nueva.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Contraseña actual y nueva son obligatorias"));
        return repo.findByUsernameAndActivoTrue(u.username())
            .filter(usr -> encoder.matches(actual, usr.getPassword()))
            .map(usr -> {
                usr.setPassword(encoder.encode(nueva));
                repo.save(usr);
                return ResponseEntity.ok(Map.<String, Object>of("ok", true));
            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Contraseña actual incorrecta")));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String tenantId = body.get("tenantId");

        var usuarioOpt = repo.findByUsernameAndActivoTrue(username)
            .filter(u -> encoder.matches(password, u.getPassword()))
            .filter(u -> perteneceAlTenant(u, tenantId));

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales incorrectas"));
        }

        var u = usuarioOpt.get();

        if (!"SUPER_ADMIN".equals(u.getRol()) && !"system".equals(u.getTenantId())) {
            boolean activo = tenantRepo.findById(u.getTenantId())
                .map(t -> t.isActivo()).orElse(false);
            if (!activo) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu barbería está desactivada",
                                 "code", "TENANT_INACTIVE"));
            }
        }

        var resp = new java.util.HashMap<String, Object>();
        resp.put("token", jwtUtil.generar(u.getUsername(), u.getRol(), u.getTenantId(), u.getBarberoId()));
        resp.put("rol", u.getRol());
        resp.put("tenantId", u.getTenantId());
        resp.put("username", u.getUsername());
        if (u.getBarberoId() != null) resp.put("barberoId", u.getBarberoId());
        return ResponseEntity.ok((Object) resp);
    }

    /**
     * SUPER_ADMIN puede autenticarse sin tenantId (accede por Ctrl+Alt+P, no por /:slug/login).
     * Cualquier otro rol DEBE enviar tenantId y coincidir exactamente con su tenant.
     * Esto evita que un admin de otra barbería acceda al generic /login como bypass.
     */
    private boolean perteneceAlTenant(Usuario u, String tenantId) {
        if ("SUPER_ADMIN".equals(u.getRol())) return true;
        return tenantId != null && !tenantId.isBlank() && tenantId.equals(u.getTenantId());
    }
}
