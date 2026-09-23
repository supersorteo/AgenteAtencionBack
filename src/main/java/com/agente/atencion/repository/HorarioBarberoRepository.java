package com.agente.atencion.repository;

import com.agente.atencion.entity.HorarioBarbero;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HorarioBarberoRepository extends JpaRepository<HorarioBarbero, Long> {
    List<HorarioBarbero> findByBarberoId(Long barberoId);
    Optional<HorarioBarbero> findByBarberoIdAndDiaSemana(Long barberoId, Integer diaSemana);
    void deleteByBarberoId(Long barberoId);
    void deleteByBarberoIdAndDiaSemana(Long barberoId, Integer diaSemana);
}
