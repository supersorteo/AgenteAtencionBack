package com.agente.atencion.security;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final Environment environment;

    @Value("${app.superadmin.username}")
    private String superAdminUsername;

    @Value("${app.superadmin.password}")
    private String superAdminPassword;

    @Value("${app.superadmin2.username}")
    private String superAdmin2Username;

    @Value("${app.superadmin2.password}")
    private String superAdmin2Password;

    public DataInitializer(UsuarioRepository repo, PasswordEncoder encoder, Environment environment) {
        this.repo = repo;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String adminPass = System.getenv("ADMIN_PASSWORD");
        boolean isProduction = Arrays.asList(environment.getActiveProfiles()).contains("production");

        if (adminPass == null || adminPass.isBlank()) {
            if (isProduction) {
                throw new IllegalStateException(
                    "ADMIN_PASSWORD env var is required in production. " +
                    "Set it in Railway variables (e.g. ADMIN_PASSWORD=admin123 for the demo).");
            }
            adminPass = "admin123";
            log.warn("ADMIN_PASSWORD not configured — using 'admin123' (dev only)");
        }

        if (repo.findByUsernameAndActivoTrue("admin").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setUsername("admin");
            admin.setPassword(encoder.encode(adminPass));
            admin.setTenantId("barberia-demo");
            admin.setRol("ADMIN");
            admin.setActivo(true);
            repo.save(admin);
            log.info("Demo admin created for barberia-demo");
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
            log.info("Super admin '{}' created", username);
        }
    }
}
