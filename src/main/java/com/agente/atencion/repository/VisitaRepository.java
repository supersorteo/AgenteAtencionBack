package com.agente.atencion.repository;

import com.agente.atencion.entity.Visita;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;

@Repository
public interface VisitaRepository extends JpaRepository<Visita, Long> {
    boolean existsByTenantIdAndPropiedadIdAndFechaAndHora(String tenantId, Long propiedadId, LocalDate fecha, String hora);
}
