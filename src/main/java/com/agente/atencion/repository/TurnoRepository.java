package com.agente.atencion.repository;

import com.agente.atencion.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    List<Turno> findByTenantIdAndFecha(String tenantId, LocalDate fecha);
    List<Turno> findByTenantIdOrderByFechaAscHoraAsc(String tenantId);
    List<Turno> findByBarberoIdAndFecha(Long barberoId, LocalDate fecha);
    List<Turno> findByBarberoId(Long barberoId);
    long countByBarberoId(Long barberoId);
    void deleteByBarberoId(Long barberoId);
    void deleteByTenantId(String tenantId);
    List<Turno> findByTenantIdAndBarberoIdOrderByFechaAscHoraAsc(String tenantId, Long barberoId);
    List<Turno> findByTenantIdAndBarberoIdAndFecha(String tenantId, Long barberoId, LocalDate fecha);
    List<Turno> findByTenantIdAndPacienteOrderByFechaDescHoraDesc(String tenantId, String paciente);

    List<Turno> findByTenantIdAndFechaBetweenOrderByFechaAscHoraAsc(String tenantId, LocalDate desde, LocalDate hasta);
    List<Turno> findByTenantIdAndBarberoIdAndFechaBetweenOrderByFechaAscHoraAsc(String tenantId, Long barberoId, LocalDate desde, LocalDate hasta);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t.fecha, COUNT(t), " +
        "SUM(CASE WHEN t.estado = 'COMPLETADO' THEN 1 ELSE 0 END) " +
        "FROM Turno t " +
        "WHERE t.tenantId = :tenantId AND t.fecha BETWEEN :desde AND :hasta " +
        "GROUP BY t.fecha ORDER BY t.fecha"
    )
    java.util.List<Object[]> findSerieDiaria(
        @org.springframework.data.repository.query.Param("tenantId") String tenantId,
        @org.springframework.data.repository.query.Param("desde") LocalDate desde,
        @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t.servicio, COUNT(t) " +
        "FROM Turno t " +
        "WHERE t.tenantId = :tenantId AND t.fecha BETWEEN :desde AND :hasta " +
        "AND t.estado <> 'CANCELADO' AND t.servicio IS NOT NULL " +
        "GROUP BY t.servicio ORDER BY COUNT(t) DESC"
    )
    java.util.List<Object[]> findRankingServicios(
        @org.springframework.data.repository.query.Param("tenantId") String tenantId,
        @org.springframework.data.repository.query.Param("desde") LocalDate desde,
        @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);

    @org.springframework.data.jpa.repository.Query(
        "SELECT t.paciente, t.telefono, COUNT(t), MAX(t.fecha) FROM Turno t " +
        "WHERE t.tenantId = :tenantId AND t.estado <> 'CANCELADO' " +
        "GROUP BY t.paciente, t.telefono ORDER BY MAX(t.fecha) DESC"
    )
    java.util.List<Object[]> findClientesSummary(@org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
