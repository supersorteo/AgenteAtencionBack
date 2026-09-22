package com.agente.atencion.repository;

import com.agente.atencion.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsernameAndActivoTrue(String username);
    List<Usuario> findByTenantIdAndActivoTrue(String tenantId);
    Optional<Usuario> findByBarberoIdAndActivoTrue(Long barberoId);
}
