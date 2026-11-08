package com.agente.atencion.service;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarberoService {

    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @Transactional
    @CacheEvict(value = "barberos", key = "#tenantId")
    public Barbero crearConHorarios(String tenantId, Barbero barbero) {
        barbero.setTenantId(tenantId);
        Barbero saved = barberoRepository.save(barbero);
        for (int dia = 2; dia <= 7; dia++) {
            HorarioBarbero h = new HorarioBarbero();
            h.setBarberoId(saved.getId());
            h.setDiaSemana(dia);
            h.setHoraInicio("09:00");
            h.setHoraFin("18:00");
            horarioRepository.save(h);
        }
        return saved;
    }

    @Transactional
    @CacheEvict(value = "barberos", key = "#tenantId")
    public void eliminarConCascade(String tenantId, Long id) {
        turnoRepository.deleteByBarberoId(id);
        bloqueoRepository.deleteByBarberoId(id);
        horarioRepository.deleteByBarberoId(id);
        usuarioRepository.findByBarberoId(id).ifPresent(usuarioRepository::delete);
        barberoRepository.findById(id)
            .filter(b -> b.getTenantId().equals(tenantId))
            .ifPresent(barberoRepository::delete);
    }

    @Transactional
    public HorarioBarbero guardarHorarioDia(Long barberoId, HorarioBarbero horario) {
        horario.setBarberoId(barberoId);
        horarioRepository.findByBarberoIdAndDiaSemana(barberoId, horario.getDiaSemana())
            .ifPresent(h -> horario.setId(h.getId()));
        return horarioRepository.save(horario);
    }

    @Transactional
    public void eliminarHorarioDia(Long barberoId, Integer dia) {
        horarioRepository.deleteByBarberoIdAndDiaSemana(barberoId, dia);
    }
}
