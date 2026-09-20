package com.agente.atencion.repository;

import com.agente.atencion.entity.Barbero;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BarberoRepository extends JpaRepository<Barbero, Long> {
    List<Barbero> findByTenantIdAndActivoTrue(String tenantId);
    List<Barbero> findByTenantId(String tenantId);
}
