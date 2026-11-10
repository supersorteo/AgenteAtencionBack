package com.agente.atencion.repository;

import com.agente.atencion.entity.Pago;
import com.agente.atencion.entity.ProveedorPago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findByReferencia(String referencia);

    Optional<Pago> findByProveedorAndExternalId(ProveedorPago proveedor, String externalId);

    List<Pago> findByTenantIdOrderByCreadoAtDesc(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pago p WHERE p.referencia = :referencia")
    Optional<Pago> bloquearPorReferencia(@Param("referencia") String referencia);
}
