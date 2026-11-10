package com.agente.atencion.service;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.BloqueoHorario;
import com.agente.atencion.entity.HorarioBarbero;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DisponibilidadService {

    @Autowired private BarberoRepository barberoRepository;
    @Autowired private HorarioBarberoRepository horarioRepository;
    @Autowired private BloqueoHorarioRepository bloqueoRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private TenantClockService tenantClockService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int INTERVALO_MINUTOS = 30;

    public record SlotDisponible(String hora, String horaFin, Long barberoId, String barberoNombre) {}

    @Cacheable(value = "disponibilidad",
               key = "#tenantId + '_' + #fecha + '_' + #servicioNombre + '_' + #barberoIdFiltro")
    public List<SlotDisponible> calcular(String tenantId, LocalDate fecha, String servicioNombre, Long barberoIdFiltro) {
        LocalDateTime ahora = LocalDateTime.now(tenantClockService.clockFor(tenantId));
        if (fecha.isBefore(ahora.toLocalDate())) return List.of();
        int duracion = obtenerDuracion(tenantId, servicioNombre);
        if (duracion <= 0 || duracion >= 24 * 60) return List.of();
        int diaSemana = fecha.getDayOfWeek().getValue(); // 1=Lun, 7=Dom

        List<Barbero> barberos = barberoIdFiltro != null
            ? barberoRepository.findById(barberoIdFiltro)
                .filter(b -> tenantId.equals(b.getTenantId()) && Boolean.TRUE.equals(b.getActivo()))
                .map(List::of).orElse(List.of())
            : barberoRepository.findByTenantIdAndActivoTrue(tenantId);

        if (barberos.isEmpty()) return List.of();

        // Bulk load in 3 queries instead of 3×N
        List<Long> barberoIds = barberos.stream().map(Barbero::getId).toList();

        Map<Long, HorarioBarbero> horariosPorBarbero = horarioRepository
            .findByBarberoIdInAndDiaSemana(barberoIds, diaSemana)
            .stream().collect(Collectors.toMap(HorarioBarbero::getBarberoId, h -> h, (a, b) -> a));

        Map<Long, List<Turno>> turnosPorBarbero = turnoRepository
            .findByBarberoIdInAndFecha(barberoIds, fecha)
            .stream().collect(Collectors.groupingBy(Turno::getBarberoId));

        Map<Long, List<BloqueoHorario>> bloqueosPorBarbero = bloqueoRepository
            .findByBarberoIdInAndFecha(barberoIds, fecha)
            .stream().collect(Collectors.groupingBy(BloqueoHorario::getBarberoId));

        List<SlotDisponible> resultado = new ArrayList<>();

        for (Barbero barbero : barberos) {
            HorarioBarbero horario = horariosPorBarbero.get(barbero.getId());
            if (horario == null) continue; // barbero no trabaja ese día

            LocalTime inicio = LocalTime.parse(horario.getHoraInicio(), FMT);
            LocalTime cierre = LocalTime.parse(horario.getHoraFin(), FMT);

            List<Turno> turnosExistentes = turnosPorBarbero.getOrDefault(barbero.getId(), List.of());
            List<BloqueoHorario> bloqueos = bloqueosPorBarbero.getOrDefault(barbero.getId(), List.of());

            int cierreMinutos = cierre.toSecondOfDay() / 60;
            for (int minuto = inicio.toSecondOfDay() / 60;
                    minuto + duracion <= cierreMinutos; minuto += INTERVALO_MINUTOS) {
                LocalTime cursor = LocalTime.of(minuto / 60, minuto % 60);
                LocalTime fin = cursor.plusMinutes(duracion);
                String horaStr = cursor.format(FMT);
                String horaFinStr = fin.format(FMT);

                boolean bloqueado = bloqueos.stream().anyMatch(b ->
                    horaStr.compareTo(b.getHoraFin()) < 0 && horaFinStr.compareTo(b.getHoraInicio()) > 0);
                if (fecha.atTime(cursor).isAfter(ahora) && !bloqueado
                        && !tieneConflicto(barbero.getId(), fecha, horaStr, horaFinStr, turnosExistentes)) {
                    resultado.add(new SlotDisponible(horaStr, horaFinStr, barbero.getId(), barbero.getNombre()));
                }
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

    public OptionalInt buscarDuracion(String tenantId, String servicioNombre) {
        return servicioRepository.findByTenantIdAndActivoTrue(tenantId).stream()
            .filter(s -> s.getNombre().equalsIgnoreCase(servicioNombre))
            .mapToInt(s -> s.getDuracionMinutos())
            .findFirst();
    }

    public boolean slotValido(String tenantId, Long barberoId, LocalDate fecha, String hora, int duracionMinutos) {
        LocalTime inicioSlot = LocalTime.parse(hora, FMT);
        if (!fecha.atTime(inicioSlot).isAfter(LocalDateTime.now(tenantClockService.clockFor(tenantId)))) return false;
        return horarioRepository.findByBarberoIdAndDiaSemana(barberoId, fecha.getDayOfWeek().getValue())
            .map(h -> {
                int ini = LocalTime.parse(h.getHoraInicio(), FMT).toSecondOfDay() / 60;
                int fin = LocalTime.parse(h.getHoraFin(), FMT).toSecondOfDay() / 60;
                int minuto = inicioSlot.toSecondOfDay() / 60;
                return minuto >= ini && minuto + duracionMinutos <= fin && (minuto - ini) % INTERVALO_MINUTOS == 0;
            })
            .orElse(false);
    }

    public String calcularHoraFin(String hora, int duracionMinutos) {
        return LocalTime.parse(hora, FMT).plusMinutes(duracionMinutos).format(FMT);
    }

    private boolean tieneConflicto(Long barberoId, LocalDate fecha, String horaInicio, String horaFin, List<Turno> existentes) {
        for (Turno t : existentes) {
            if ("CANCELADO".equals(t.getEstado())) continue;
            String finExistente = t.getHoraFin() != null ? t.getHoraFin()
                : LocalTime.parse(t.getHora(), FMT).plusMinutes(30).format(FMT);
            if (horaInicio.compareTo(finExistente) < 0 && horaFin.compareTo(t.getHora()) > 0) {
                return true;
            }
        }
        return false;
    }
}
