package com.agente.atencion.repository;

import com.agente.atencion.entity.Barbero;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BarberoRepository extends JpaRepository<Barbero, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Barbero b WHERE b.id = :id AND b.tenantId = :tenantId AND b.activo = true")
    Optional<Barbero> bloquearParaReserva(@Param("tenantId") String tenantId, @Param("id") Long id);
    List<Barbero> findByTenantIdAndActivoTrue(String tenantId);
    List<Barbero> findByTenantId(String tenantId);
}
