package com.agente.atencion.tools;

import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.TurnoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class NegocioTools {

    @Autowired
    private TurnoRepository turnoRepository;

    @Tool(description = "Consulta los horarios disponibles para una fecha (formato YYYY-MM-DD) y un tenantId de negocio")
    public String consultarDisponibilidad(String tenantId, String fecha) {
        List<String> todosLosHorarios = List.of("09:00", "10:00", "11:00", "14:00", "15:00", "16:00", "17:00");
        List<Turno> reservados = turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha));
        List<String> ocupados = reservados.stream().map(Turno::getHora).toList();
        List<String> libres = todosLosHorarios.stream().filter(h -> !ocupados.contains(h)).toList();
        if (libres.isEmpty()) return "No hay turnos disponibles para el " + fecha;
        return "Horarios disponibles para el " + fecha + ": " + String.join(", ", libres);
    }

    @Tool(description = "Reserva un turno. Requiere: tenantId, nombre del paciente/cliente, servicio solicitado (null si no aplica), fecha (YYYY-MM-DD) y hora (HH:mm)")
    public String reservarTurno(String tenantId, String paciente, String servicio, String fecha, String hora) {
        List<Turno> existentes = turnoRepository.findByTenantIdAndFecha(tenantId, LocalDate.parse(fecha));
        boolean ocupado = existentes.stream().anyMatch(t -> t.getHora().equals(hora));
        if (ocupado) return "El horario " + hora + " del " + fecha + " ya está ocupado. Por favor elegí otro horario.";
        Turno turno = new Turno();
        turno.setTenantId(tenantId);
        turno.setPaciente(paciente);
        turno.setServicio(servicio);
        turno.setFecha(LocalDate.parse(fecha));
        turno.setHora(hora);
        turnoRepository.save(turno);
        return "✅ Turno confirmado para " + paciente + " el " + fecha + " a las " + hora + ". ¡Te esperamos!";
    }
}
