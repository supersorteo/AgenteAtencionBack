package com.agente.atencion.repository;

import com.agente.atencion.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByTenantIdAndFecha(String tenantId, LocalDate fecha);
    List<Turno> findByTenantIdOrderByFechaAscHoraAsc(String tenantId);
    List<Turno> findByBarberoIdAndFecha(Long barberoId, LocalDate fecha);
    List<Turno> findByTenantIdAndBarberoIdOrderByFechaAscHoraAsc(String tenantId, Long barberoId);
    List<Turno> findByTenantIdAndBarberoIdAndFecha(String tenantId, Long barberoId, LocalDate fecha);
    List<Turno> findByTenantIdAndPacienteOrderByFechaDescHoraDesc(String tenantId, String paciente);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t.paciente, t.telefono, COUNT(t), MAX(t.fecha) FROM Turno t " +
        "WHERE t.tenantId = :tenantId AND t.estado <> 'CANCELADO' " +
        "GROUP BY t.paciente, t.telefono ORDER BY MAX(t.fecha) DESC"
    )
    java.util.List<Object[]> findClientesSummary(@org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
