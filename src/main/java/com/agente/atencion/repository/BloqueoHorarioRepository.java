package com.agente.atencion.repository;

import com.agente.atencion.entity.BloqueoHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface BloqueoHorarioRepository extends JpaRepository<BloqueoHorario, Long> {
    List<BloqueoHorario> findByTenantId(String tenantId);
    List<BloqueoHorario> findByTenantIdAndBarberoId(String tenantId, Long barberoId);
    void deleteByBarberoId(Long barberoId);
    List<BloqueoHorario> findByBarberoIdAndFecha(Long barberoId, LocalDate fecha);

    @Query("SELECT b FROM BloqueoHorario b WHERE b.barberoId = :barberoId AND b.fecha = :fecha " +
           "AND b.horaInicio < :horaFin AND b.horaFin > :horaInicio")
    List<BloqueoHorario> findConflictos(@Param("barberoId") Long barberoId,
                                        @Param("fecha") LocalDate fecha,
                                        @Param("horaInicio") String horaInicio,
                                        @Param("horaFin") String horaFin);
}
