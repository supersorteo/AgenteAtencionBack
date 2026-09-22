package com.agente.atencion.security;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public DataInitializer(UsuarioRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        String adminPass = System.getenv("ADMIN_PASSWORD");
        if (adminPass == null || adminPass.isBlank()) {
            adminPass = "admin123";
            System.out.println("[WARN] ADMIN_PASSWORD no configurado. Usando 'admin123' (solo desarrollo).");
        }
        if (repo.findByUsernameAndActivoTrue("admin").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode(adminPass));
            admin.setTenantId("barberia-demo");
            admin.setRol("ADMIN");
            admin.setActivo(true);
            repo.save(admin);
            System.out.println("[INFO] Usuario admin creado.");
        }
    }
}
