package com.agente.atencion.repository;

import com.agente.atencion.entity.CategoriaServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoriaServicioRepository extends JpaRepository<CategoriaServicio, Long> {
    List<CategoriaServicio> findByTenantIdAndActivoTrueOrderByOrdenAsc(String tenantId);
    boolean existsByTenantId(String tenantId);
}
