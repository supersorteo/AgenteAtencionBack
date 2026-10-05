package com.agente.atencion.security;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    @Value("${app.superadmin.username}")
    private String superAdminUsername;

    @Value("${app.superadmin.password}")
    private String superAdminPassword;

    @Value("${app.superadmin2.username}")
    private String superAdmin2Username;

    @Value("${app.superadmin2.password}")
    private String superAdmin2Password;

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
            System.out.println("[INFO] Usuario admin de barberia-demo creado.");
        }

        crearSuperAdminSiNoExiste(superAdminUsername, superAdminPassword);
        crearSuperAdminSiNoExiste(superAdmin2Username, superAdmin2Password);
    }

    private void crearSuperAdminSiNoExiste(String username, String password) {
        if (repo.findByUsernameAndActivoTrue(username).isEmpty()) {
            Usuario sa = new Usuario();
            sa.setUsername(username);
            sa.setPassword(encoder.encode(password));
            sa.setTenantId("system");
            sa.setRol("SUPER_ADMIN");
            sa.setActivo(true);
            repo.save(sa);
            System.out.println("[INFO] Super admin '" + username + "' creado.");
        }
    }
}
