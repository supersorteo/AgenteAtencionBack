package com.agente.atencion.security;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final BarberoRepository barberoRepo;

    public DataInitializer(UsuarioRepository repo, PasswordEncoder encoder, BarberoRepository barberoRepo) {
        this.repo = repo;
        this.encoder = encoder;
        this.barberoRepo = barberoRepo;
    }

    @Override
    public void run(String... args) {
        // Admin
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

        // Barbero: Juan García → usuario 'juan' con rol BARBERO
        if (repo.findByUsernameAndActivoTrue("juan").isEmpty()) {
            barberoRepo.findByTenantIdAndActivoTrue("barberia-demo").stream()
                .filter(b -> b.getNombre().equalsIgnoreCase("Juan García"))
                .findFirst()
                .ifPresent(b -> {
                    String barberoPass = System.getenv("BARBERO_PASSWORD");
                    if (barberoPass == null || barberoPass.isBlank()) {
                        barberoPass = "juan123";
                        System.out.println("[WARN] BARBERO_PASSWORD no configurado. Usando 'juan123' (solo desarrollo).");
                    }
                    Usuario barbero = new Usuario();
                    barbero.setUsername("juan");
                    barbero.setPassword(encoder.encode(barberoPass));
                    barbero.setTenantId("barberia-demo");
                    barbero.setRol("BARBERO");
                    barbero.setBarberoId(b.getId());
                    barbero.setActivo(true);
                    repo.save(barbero);
                    System.out.println("[INFO] Usuario barbero 'juan' creado (barberoId=" + b.getId() + ").");
                });
        }
    }
}
