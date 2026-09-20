package com.agente.atencion.repository;

import com.agente.atencion.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    List<Servicio> findByTenantIdAndActivoTrue(String tenantId);
    List<Servicio> findByTenantId(String tenantId);
}
