package com.agente.atencion.tools;

import com.agente.atencion.entity.Propiedad;
import com.agente.atencion.entity.Visita;
import com.agente.atencion.repository.PropiedadRepository;
import com.agente.atencion.repository.VisitaRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class InmobiliariaTools {

    @Autowired private PropiedadRepository propiedadRepository;
    @Autowired private VisitaRepository visitaRepository;

    @Tool(description = "Busca propiedades disponibles para venta o alquiler. " +
        "tipo: apartamento, casa, local, terreno (null si no especifica). " +
        "operacion: venta o alquiler (null si no especifica). " +
        "zona: barrio o zona de Montevideo (null si no especifica). " +
        "precioMax: precio máximo en USD, usar 0.0 si no especifica. " +
        "habitaciones: cantidad mínima de habitaciones, usar 0 si no especifica.")
    public String buscarPropiedades(String tenantId, String tipo, String operacion,
                                     String zona, Double precioMax, Integer habitaciones) {
        List<Propiedad> resultados = propiedadRepository.buscar(
            tenantId, tipo, operacion, zona,
            precioMax == null ? 0.0 : precioMax,
            habitaciones == null ? 0 : habitaciones
        );
        if (resultados.isEmpty())
            return "No encontré propiedades con esos criterios. ¿Querés ajustar la búsqueda?";

        StringBuilder sb = new StringBuilder("Encontré " + resultados.size() + " propiedad(es):\n\n");
        for (Propiedad p : resultados) {
            sb.append("ID #").append(p.getId()).append(" — ")
              .append(p.getTipo()).append(" en ").append(p.getZona())
              .append(" (").append(p.getOperacion()).append(")\n");
            sb.append("Precio: ").append(p.getMoneda()).append(" ").append(String.format("%.0f", p.getPrecio()));
            if (p.getHabitaciones() != null) sb.append(" | ").append(p.getHabitaciones()).append(" hab.");
            if (p.getBanos() != null) sb.append(" | ").append(p.getBanos()).append(" baños");
            if (p.getMetrosCuadrados() != null) sb.append(" | ").append(p.getMetrosCuadrados()).append(" m²");
            sb.append("\n").append(p.getDescripcion()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool(description = "Agenda una visita a una propiedad. " +
        "propiedadId: número ID de la propiedad (obligatorio). " +
        "nombreCliente: nombre completo del cliente. " +
        "telefono: teléfono de contacto. " +
        "fecha: fecha de la visita en formato YYYY-MM-DD. " +
        "hora: hora en formato HH:mm.")
    public String agendarVisita(String tenantId, Long propiedadId, String nombreCliente,
                                 String telefono, String fecha, String hora) {
        if (!propiedadRepository.existsById(propiedadId))
            return "No encontré la propiedad con ID #" + propiedadId + ". Verificá el número.";

        boolean ocupado = visitaRepository.existsByTenantIdAndPropiedadIdAndFechaAndHora(
            tenantId, propiedadId, LocalDate.parse(fecha), hora);
        if (ocupado)
            return "Esa fecha y hora ya tiene una visita agendada para esa propiedad. ¿Querés otro horario?";

        Visita visita = new Visita();
        visita.setTenantId(tenantId);
        visita.setPropiedadId(propiedadId);
        visita.setNombreCliente(nombreCliente);
        visita.setTelefono(telefono);
        visita.setFecha(LocalDate.parse(fecha));
        visita.setHora(hora);
        visitaRepository.save(visita);

        return "✅ Visita confirmada para " + nombreCliente + " el " + fecha + " a las " + hora +
               ". Un asesor se contactará al " + telefono + " para coordinar los detalles.";
    }
}
