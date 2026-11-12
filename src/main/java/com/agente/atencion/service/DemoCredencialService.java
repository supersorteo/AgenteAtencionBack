package com.agente.atencion.service;

import com.agente.atencion.entity.DemoCredencial;
import com.agente.atencion.repository.DemoCredencialRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DemoCredencialService {

    private final DemoCredencialRepository repo;

    public DemoCredencialService(DemoCredencialRepository repo) {
        this.repo = repo;
    }

    public Optional<DemoCredencial> obtener() {
        return repo.findById(SuscripcionService.DEMO_SLUG);
    }

    // Solo se llama al reiniciar la contraseña del administrador de la demo desde el superadmin
    public void actualizar(String username, String password) {
        DemoCredencial c = repo.findById(SuscripcionService.DEMO_SLUG).orElseGet(() -> {
            DemoCredencial nueva = new DemoCredencial();
            nueva.setTenantId(SuscripcionService.DEMO_SLUG);
            return nueva;
        });
        c.setUsername(username);
        c.setPassword(password);
        repo.save(c);
    }
}
