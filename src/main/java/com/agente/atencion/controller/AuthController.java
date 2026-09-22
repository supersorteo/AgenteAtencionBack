package com.agente.atencion.controller;

import com.agente.atencion.entity.Usuario;
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
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;

    public AuthController(UsuarioRepository repo, JwtUtil jwtUtil, PasswordEncoder encoder) {
        this.repo = repo;
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
        return repo.findByUsernameAndActivoTrue(username)
            .filter(u -> encoder.matches(password, u.getPassword()))
            .map(u -> {
                var resp = new java.util.HashMap<String, Object>();
                resp.put("token", jwtUtil.generar(u.getUsername(), u.getRol(), u.getTenantId(), u.getBarberoId()));
                resp.put("rol", u.getRol());
                resp.put("tenantId", u.getTenantId());
                resp.put("username", u.getUsername());
                if (u.getBarberoId() != null) resp.put("barberoId", u.getBarberoId());
                return ResponseEntity.ok((Object) resp);
            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Credenciales incorrectas")));
    }
}
