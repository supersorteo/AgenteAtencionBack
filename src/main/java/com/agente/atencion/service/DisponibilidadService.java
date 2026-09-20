package com.agente.atencion.service;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DisponibilidadService {

    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private ServicioRepository servicioRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int INTERVALO_MINUTOS = 30;

    public record SlotDisponible(String hora, String horaFin, Long barberoId, String barberoNombre) {}

    public List<SlotDisponible> calcular(String tenantId, LocalDate fecha, String servicioNombre, Long barberoIdFiltro) {
        int duracion = obtenerDuracion(tenantId, servicioNombre);
        int diaSemana = fecha.getDayOfWeek().getValue(); // 1=Lun, 7=Dom

        List<Barbero> barberos = barberoIdFiltro != null
            ? barberoRepository.findById(barberoIdFiltro).map(List::of).orElse(List.of())
            : barberoRepository.findByTenantIdAndActivoTrue(tenantId);

        List<SlotDisponible> resultado = new ArrayList<>();

        for (Barbero barbero : barberos) {
            Optional<HorarioBarbero> horarioOpt = horarioRepository
                .findByBarberoIdAndDiaSemana(barbero.getId(), diaSemana);
            if (horarioOpt.isEmpty()) continue; // barbero no trabaja ese día

            HorarioBarbero horario = horarioOpt.get();
            LocalTime inicio = LocalTime.parse(horario.getHoraInicio(), FMT);
            LocalTime cierre = LocalTime.parse(horario.getHoraFin(), FMT);

            List<Turno> turnosExistentes = turnoRepository
                .findByBarberoIdAndFecha(barbero.getId(), fecha);

            LocalTime cursor = inicio;
            while (!cursor.plusMinutes(duracion).isAfter(cierre)) {
                LocalTime fin = cursor.plusMinutes(duracion);
                String horaStr = cursor.format(FMT);
                String horaFinStr = fin.format(FMT);

                if (!tieneConflicto(barbero.getId(), fecha, horaStr, horaFinStr, turnosExistentes)) {
                    resultado.add(new SlotDisponible(horaStr, horaFinStr, barbero.getId(), barbero.getNombre()));
                }
                cursor = cursor.plusMinutes(INTERVALO_MINUTOS);
            }
        }

        resultado.sort(Comparator.comparing(SlotDisponible::hora));
        return resultado;
    }

    public boolean validar(Long barberoId, LocalDate fecha, String horaInicio, String horaFin) {
        List<Turno> existentes = turnoRepository.findByBarberoIdAndFecha(barberoId, fecha);
        if (tieneConflicto(barberoId, fecha, horaInicio, horaFin, existentes)) return false;
        List<?> bloqueos = bloqueoRepository.findConflictos(barberoId, fecha, horaInicio, horaFin);
        return bloqueos.isEmpty();
    }

    public int obtenerDuracion(String tenantId, String servicioNombre) {
        return servicioRepository.findByTenantIdAndActivoTrue(tenantId).stream()
            .filter(s -> s.getNombre().equalsIgnoreCase(servicioNombre))
            .map(s -> s.getDuracionMinutos())
            .findFirst()
            .orElse(30);
    }

    public String calcularHoraFin(String hora, int duracionMinutos) {
        return LocalTime.parse(hora, FMT).plusMinutes(duracionMinutos).format(FMT);
    }

    private boolean tieneConflicto(Long barberoId, LocalDate fecha, String horaInicio, String horaFin, List<Turno> existentes) {
        // Regla: conflicto si inicio < finExistente AND fin > inicioExistente
        for (Turno t : existentes) {
            String finExistente = t.getHoraFin() != null ? t.getHoraFin()
                : LocalTime.parse(t.getHora(), FMT).plusMinutes(30).format(FMT);
            if (horaInicio.compareTo(finExistente) < 0 && horaFin.compareTo(t.getHora()) > 0) {
                return true;
            }
        }
        return false;
    }
}
