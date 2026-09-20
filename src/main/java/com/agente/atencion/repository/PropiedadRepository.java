package com.agente.atencion.repository;

import com.agente.atencion.entity.Propiedad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropiedadRepository extends JpaRepository<Propiedad, Long> {

    @Query("SELECT p FROM Propiedad p WHERE p.tenantId = :tenantId AND p.disponible = true " +
           "AND (:tipo IS NULL OR LOWER(p.tipo) LIKE LOWER(CONCAT('%', :tipo, '%'))) " +
           "AND (:operacion IS NULL OR LOWER(p.operacion) = LOWER(:operacion)) " +
           "AND (:zona IS NULL OR LOWER(p.zona) LIKE LOWER(CONCAT('%', :zona, '%'))) " +
           "AND (:precioMax = 0 OR p.precio <= :precioMax) " +
           "AND (:habitaciones = 0 OR p.habitaciones >= :habitaciones)")
    List<Propiedad> buscar(@Param("tenantId") String tenantId,
                           @Param("tipo") String tipo,
                           @Param("operacion") String operacion,
                           @Param("zona") String zona,
                           @Param("precioMax") Double precioMax,
                           @Param("habitaciones") Integer habitaciones);
}
