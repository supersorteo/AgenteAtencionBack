package com.agente.atencion.repository;

import com.agente.atencion.entity.Galeria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GaleriaRepository extends JpaRepository<Galeria, Long> {
    List<Galeria> findByTenantIdAndActivoTrue(String tenantId);
    List<Galeria> findByTenantId(String tenantId);
}
