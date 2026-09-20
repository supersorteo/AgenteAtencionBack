package com.agente.atencion.tools;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.service.DisponibilidadService;
import com.agente.atencion.service.TenantContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Component
public class NegocioTools {

    @Autowired private TurnoRepository turnoRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadService disponibilidadService;

    @Tool(description = "Retorna la fecha actual y los próximos 7 días con su nombre de día. Usá esta herramienta cuando el cliente diga 'mañana', 'pasado mañana', 'el lunes', 'esta semana' u otra referencia relativa a la fecha.")
    public String obtenerFechaActual() {
        LocalDate hoy = LocalDate.now();
        Locale es = new Locale("es", "UY");
        StringBuilder sb = new StringBuilder();
        sb.append("HOY es ").append(hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, es))
          .append(" ").append(hoy).append("\n");
        for (int i = 1; i <= 7; i++) {
            LocalDate d = hoy.plusDays(i);
            String nombre = i == 1 ? "mañana" : i == 2 ? "pasado mañana"
                : d.getDayOfWeek().getDisplayName(TextStyle.FULL, es);
            sb.append(nombre).append(" = ").append(d).append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "Consulta los servicios activos y sus precios actualizados del negocio")
    public String buscarServicios() {
        String tenantId = TenantContext.get();
        List<Servicio> servicios = servicioRepository.findByTenantIdAndActivoTrue(tenantId);
        if (servicios.isEmpty()) return "No hay servicios disponibles en este momento.";
        StringBuilder sb = new StringBuilder("Servicios disponibles:\n");
        for (Servicio s : servicios) {
            sb.append(s.getEmoji()).append(" ").append(s.getNombre())
              .append(" — $").append(s.getPrecio().intValue()).append(" UYU")
              .append(" (").append(s.getDuracionMinutos()).append(" min)");
            if (s.getDescripcion() != null && !s.getDescripcion().isBlank())
                sb.append(": ").append(s.getDescripcion());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "Consulta los barberos disponibles en la barberia")
    public String buscarBarberos() {
        String tenantId = TenantContext.get();
        List<Barbero> barberos = barberoRepository.findByTenantIdAndActivoTrue(tenantId);
        if (barberos.isEmpty()) return "No hay barberos registrados actualmente.";
        StringBuilder sb = new StringBuilder("Barberos disponibles:\n");
        for (Barbero b : barberos) {
            sb.append("- ").append(b.getNombre());
            if (b.getEspecialidad() != null) sb.append(" (").append(b.getEspecialidad()).append(")");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "Consulta los horarios disponibles para una fecha (YYYY-MM-DD) y un servicio especifico. Opcionalmente puede especificarse el nombre del barbero.")
    public String consultarDisponibilidad(String fecha, String servicio, String nombreBarbero) {
        LocalDate fechaLocal = LocalDate.parse(fecha);
        if (fechaLocal.isBefore(LocalDate.now()))
            return "No es posible reservar en fechas pasadas. Por favor elegí una fecha a partir de hoy (" + LocalDate.now() + ").";
        String tenantId = TenantContext.get();
        Long barberoId = null;
        if (nombreBarbero != null && !nombreBarbero.isBlank()) {
            barberoId = barberoRepository.findByTenantIdAndActivoTrue(tenantId).stream()
                .filter(b -> b.getNombre().equalsIgnoreCase(nombreBarbero))
                .map(Barbero::getId)
                .findFirst()
                .orElse(null);
        }
        List<DisponibilidadService.SlotDisponible> slots =
            disponibilidadService.calcular(tenantId, fechaLocal, servicio, barberoId);
        if (slots.isEmpty())
            return "No hay turnos disponibles para el " + fecha + " para el servicio " + servicio + ".";
        StringBuilder sb = new StringBuilder("Horarios disponibles para " + servicio + " el " + fecha + ":\n");
        for (DisponibilidadService.SlotDisponible slot : slots) {
            sb.append("- ").append(slot.hora()).append(" a ").append(slot.horaFin())
              .append(" con ").append(slot.barberoNombre()).append("\n");
        }
        return sb.toString().trim();
    }

    @Tool(description = "Reserva un turno para el cliente. Requiere: nombre completo, servicio, fecha (YYYY-MM-DD), hora (HH:mm). Opcionales: telefono (para WhatsApp) y nombreBarbero.")
    public String reservarTurno(String paciente, String servicio, String fecha, String hora, String telefono, String nombreBarbero) {
        String tenantId = TenantContext.get();
        LocalDate fechaLocal = LocalDate.parse(fecha);
        if (fechaLocal.isBefore(LocalDate.now()))
            return "No es posible reservar en fechas pasadas. Pedile al cliente una fecha válida a partir de hoy (" + LocalDate.now() + ").";

        // Resolver barbero
        Long barberoId = null;
        String barberoNombre = null;
        if (nombreBarbero != null && !nombreBarbero.isBlank()) {
            var opt = barberoRepository.findByTenantIdAndActivoTrue(tenantId).stream()
                .filter(b -> b.getNombre().equalsIgnoreCase(nombreBarbero))
                .findFirst();
            if (opt.isPresent()) { barberoId = opt.get().getId(); barberoNombre = opt.get().getNombre(); }
        }
        // Si no se especificó barbero, buscar el primero disponible para ese slot
        if (barberoId == null) {
            var slots = disponibilidadService.calcular(tenantId, fechaLocal, servicio, null);
            var slot = slots.stream().filter(s -> s.hora().equals(hora)).findFirst();
            if (slot.isEmpty())
                return "El horario " + hora + " del " + fecha + " no está disponible para " + servicio + ". Usá consultarDisponibilidad para ver los horarios libres.";
            barberoId = slot.get().barberoId();
            barberoNombre = slot.get().barberoNombre();
        } else {
            // Validar que el slot esté libre para el barbero especificado
            int duracion = disponibilidadService.obtenerDuracion(tenantId, servicio);
            String horaFin = disponibilidadService.calcularHoraFin(hora, duracion);
            if (!disponibilidadService.validar(barberoId, fechaLocal, hora, horaFin))
                return "El horario " + hora + " del " + fecha + " ya está ocupado para " + nombreBarbero + ". Por favor elegí otro horario.";
        }

        int duracion = disponibilidadService.obtenerDuracion(tenantId, servicio);
        String horaFin = disponibilidadService.calcularHoraFin(hora, duracion);

        Turno turno = new Turno();
        turno.setTenantId(tenantId);
        turno.setBarberoId(barberoId);
        turno.setPaciente(paciente);
        turno.setServicio(servicio);
        turno.setFecha(fechaLocal);
        turno.setHora(hora);
        turno.setHoraFin(horaFin);
        if (telefono != null && !telefono.isBlank()) turno.setTelefono(telefono);
        turnoRepository.save(turno);

        String confirmacion = "Turno confirmado para " + paciente + " el " + fecha +
               " de " + hora + " a " + horaFin +
               " con " + barberoNombre + ". Servicio: " + servicio + ".";
        if (telefono != null && !telefono.isBlank())
            confirmacion += " Te vamos a contactar al " + telefono + ".";
        confirmacion += " ¡Te esperamos!";
        return confirmacion;
    }
}
