package com.agente.atencion.service;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.repository.TurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ReservaAgenteService {
    private final TurnoRepository turnos;
    private final ServicioRepository servicios;
    private final BarberoRepository barberos;
    private final DisponibilidadService disponibilidad;
    private final Clock reloj;

    public ReservaAgenteService(TurnoRepository turnos, ServicioRepository servicios,
            BarberoRepository barberos, DisponibilidadService disponibilidad, Clock reloj) {
        this.turnos = turnos;
        this.servicios = servicios;
        this.barberos = barberos;
        this.disponibilidad = disponibilidad;
        this.reloj = reloj;
    }

    public record ReservaConfirmada(Turno turno, String barberoNombre) {}

    public Servicio resolverServicio(String tenantId, String nombre) {
        var encontrados = servicios.findByTenantIdAndActivoTrue(tenantId).stream()
            .filter(s -> normalizar(s.getNombre()).equals(normalizar(nombre))).toList();
        if (encontrados.size() != 1) {
            throw new IllegalArgumentException("Elegí un servicio de buscarServicios; el indicado no existe, está inactivo o es ambiguo.");
        }
        Servicio servicio = encontrados.getFirst();
        if (servicio.getDuracionMinutos() == null || servicio.getDuracionMinutos() <= 0
                || servicio.getDuracionMinutos() >= 24 * 60) {
            throw new IllegalArgumentException("Ese servicio no tiene una duración válida configurada. Consultá al negocio.");
        }
        return servicio;
    }

    public Barbero resolverBarbero(String tenantId, String nombre) {
        var encontrados = barberos.findByTenantIdAndActivoTrue(tenantId).stream()
            .filter(b -> normalizar(b.getNombre()).equals(normalizar(nombre))).toList();
        if (encontrados.size() != 1) {
            throw new IllegalArgumentException("Elegí un barbero de buscarBarberos; el indicado no existe, está inactivo o es ambiguo. No se asignó otro barbero.");
        }
        return encontrados.getFirst();
    }

    public LocalDate resolverFecha(String fecha) {
        try {
            if (fecha == null || !fecha.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("Formato", "", 0);
            LocalDate dia = LocalDate.parse(fecha);
            if (dia.isBefore(LocalDate.now(reloj))) {
                throw new IllegalArgumentException("No es posible reservar en fechas pasadas. Hoy es " + LocalDate.now(reloj) + ".");
            }
            return dia;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Pedile al cliente una fecha válida y usá el formato YYYY-MM-DD.");
        }
    }

    @Transactional
    public ReservaConfirmada reservar(String tenantId, String paciente, String telefono,
            String servicio, String nombreBarbero, String fecha, String hora) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalStateException("Falta el contexto del negocio");
        List<String> faltantes = new ArrayList<>();
        requerido(faltantes, "nombre", paciente);
        requerido(faltantes, "teléfono", telefono);
        requerido(faltantes, "servicio", servicio);
        requerido(faltantes, "barbero", nombreBarbero);
        requerido(faltantes, "fecha", fecha);
        requerido(faltantes, "hora", hora);
        if (!faltantes.isEmpty()) throw new IllegalArgumentException("Faltan datos obligatorios: " + String.join(", ", faltantes) + ". Solicitálos antes de reservar.");
        String nombre = paciente.strip();
        if (nombre.length() < 2 || nombre.length() > 100 || !nombre.matches(".*\\p{L}.*")) {
            throw new IllegalArgumentException("Pedile al cliente un nombre válido (entre 2 y 100 caracteres).");
        }
        String contacto = telefono.strip();
        String digitos = contacto.replaceAll("\\D", "");
        if (!contacto.matches("\\+?[0-9\\s().-]+") || digitos.length() < 8 || digitos.length() > 15) {
            throw new IllegalArgumentException("Pedile un teléfono válido, con 8 a 15 dígitos y código de país si corresponde.");
        }
        contacto = (contacto.startsWith("+") ? "+" : "") + digitos;
        LocalDate dia = resolverFecha(fecha);
        LocalTime inicio;
        try {
            if (!hora.matches("\\d{2}:\\d{2}")) throw new DateTimeParseException("Formato", "", 0);
            inicio = LocalTime.parse(hora);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Pedile una hora válida y usá el formato HH:mm de 24 horas.");
        }
        if (!dia.atTime(inicio).isAfter(LocalDateTime.now(reloj))) {
            throw new IllegalArgumentException("Ese horario ya pasó. Consultá nuevamente los horarios disponibles.");
        }
        Servicio elegido = resolverServicio(tenantId, servicio);
        Barbero candidato = resolverBarbero(tenantId, nombreBarbero);
        // Serialize reservations for this barber until the transaction commits.
        Barbero barbero = barberos.bloquearParaReserva(tenantId, candidato.getId())
            .orElseThrow(() -> new IllegalArgumentException("El barbero ya no está disponible. Consultá buscarBarberos."));
        var slot = disponibilidad.calcular(tenantId, dia, elegido.getNombre(), barbero.getId()).stream()
            .filter(s -> s.barberoId().equals(barbero.getId()) && s.hora().equals(hora))
            .findFirst().orElseThrow(() -> new IllegalArgumentException(
                "Ese horario no está disponible para el servicio y barbero elegidos. Consultá consultarDisponibilidad y pedí otra opción; no cambies el barbero automáticamente."));

        Turno turno = new Turno();
        turno.setTenantId(tenantId);
        turno.setPaciente(nombre);
        turno.setTelefono(contacto);
        turno.setServicio(elegido.getNombre());
        turno.setBarberoId(barbero.getId());
        turno.setFecha(dia);
        turno.setHora(slot.hora());
        turno.setHoraFin(slot.horaFin());
        turno.setEstado("RESERVADO");
        turno.setCreadoEn(LocalDateTime.now(reloj));
        return new ReservaConfirmada(turnos.saveAndFlush(turno), barbero.getNombre());
    }

    private static void requerido(List<String> faltantes, String campo, String valor) {
        if (valor == null || valor.isBlank()) faltantes.add(campo);
    }

    private static String normalizar(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor.strip(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
