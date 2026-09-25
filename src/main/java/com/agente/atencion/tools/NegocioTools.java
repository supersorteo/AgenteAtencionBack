package com.agente.atencion.tools;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.service.DisponibilidadService;
import com.agente.atencion.service.TenantClockService;
import com.agente.atencion.service.TenantContext;
import com.agente.atencion.service.ReservaAgenteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NegocioTools {

    private static final Logger log = LoggerFactory.getLogger(NegocioTools.class);

    @Autowired private ReservaAgenteService reservas;
    @Autowired private TenantClockService tenantClockService;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadService disponibilidadService;

    @Tool(description = "Retorna la fecha actual y los próximos 7 días con su nombre de día. Usá esta herramienta cuando el cliente diga 'mañana', 'pasado mañana', 'el lunes', 'esta semana' u otra referencia relativa a la fecha.")
    public String obtenerFechaActual() {
        LocalDate hoy = LocalDate.now(tenantClockService.clockFor(TenantContext.get()));
        Locale es = Locale.forLanguageTag("es-UY");
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
        Map<String, List<Servicio>> porCategoria = new LinkedHashMap<>();
        for (Servicio s : servicios) {
            String cat = s.getCategoria() != null && !s.getCategoria().isBlank() ? s.getCategoria() : "Otros";
            porCategoria.computeIfAbsent(cat, k -> new ArrayList<>()).add(s);
        }
        StringBuilder sb = new StringBuilder("Servicios disponibles:\n");
        for (Map.Entry<String, List<Servicio>> entry : porCategoria.entrySet()) {
            sb.append("\n## ").append(entry.getKey()).append("\n");
            for (Servicio s : entry.getValue()) {
                sb.append(s.getEmoji()).append(" ").append(s.getNombre())
                  .append(" — $").append(s.getPrecio().intValue()).append(" UYU")
                  .append(" (").append(s.getDuracionMinutos()).append(" min)");
                if (s.getDescripcion() != null && !s.getDescripcion().isBlank())
                    sb.append(": ").append(s.getDescripcion());
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    @Tool(description = "Consulta los barberos disponibles en la barberia")
    public String buscarBarberos() {
        String tenantId = TenantContext.get();
        List<Barbero> barberos = barberoRepository.findByTenantIdAndActivoTrue(tenantId);
        log.info("[buscarBarberos] tenantId={} encontrados={} nombres={}", tenantId, barberos.size(),
            barberos.stream().map(Barbero::getNombre).toList());
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
    public String consultarDisponibilidad(String fecha, String servicio,
            @ToolParam(required = false, description = "Nombre del barbero elegido; omitilo para consultar todos, sin asignar automáticamente") String nombreBarbero) {
        try {
            LocalDate fechaLocal = reservas.resolverFecha(fecha);
            String tenantId = TenantContext.get();
            Servicio elegido = reservas.resolverServicio(tenantId, servicio);
            Long barberoId = null;
            if (nombreBarbero != null && !nombreBarbero.isBlank()) {
                barberoId = reservas.resolverBarbero(tenantId, nombreBarbero).getId();
            }
            List<DisponibilidadService.SlotDisponible> slots =
                disponibilidadService.calcular(tenantId, fechaLocal, elegido.getNombre(), barberoId);
            if (slots.isEmpty())
                return "No hay turnos disponibles para el " + fecha + " para el servicio " + elegido.getNombre() + ".";
            StringBuilder sb = new StringBuilder("Horarios disponibles para " + elegido.getNombre() + " el " + fecha + ":\n");
            for (DisponibilidadService.SlotDisponible slot : slots) {
                sb.append("- ").append(slot.hora()).append(" a ").append(slot.horaFin())
                  .append(" con ").append(slot.barberoNombre()).append("\n");
            }
            return sb.toString().trim();
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

}
