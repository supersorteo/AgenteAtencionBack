package com.agente.atencion.repository;

import com.agente.atencion.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByTenantIdAndFecha(String tenantId, LocalDate fecha);
    List<Turno> findByTenantIdOrderByFechaAscHoraAsc(String tenantId);
}
