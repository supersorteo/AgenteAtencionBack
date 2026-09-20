package com.agente.atencion.tools;

import com.agente.atencion.entity.Propiedad;
import com.agente.atencion.entity.Visita;
import com.agente.atencion.repository.PropiedadRepository;
import com.agente.atencion.repository.VisitaRepository;
import com.agente.atencion.service.TenantContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class InmobiliariaTools {

    @Autowired private PropiedadRepository propiedadRepository;
    @Autowired private VisitaRepository visitaRepository;

    @Tool(description = "Busca propiedades disponibles. " +
        "tipo: apartamento, casa, local, terreno (null si no especifica). " +
        "operacion: venta o alquiler (null si no especifica). " +
        "zona: barrio de Montevideo (null si no especifica). " +
        "precioMax: precio maximo en USD, usar 0.0 si no especifica. " +
        "habitaciones: cantidad minima de habitaciones, usar 0 si no especifica.")
    public String buscarPropiedades(String tipo, String operacion,
                                    String zona, Double precioMax, Integer habitaciones) {
        String tenantId = TenantContext.get();
        List<Propiedad> resultados = propiedadRepository.buscar(
            tenantId, tipo, operacion, zona,
            precioMax == null ? 0.0 : precioMax,
            habitaciones == null ? 0 : habitaciones
        );
        if (resultados.isEmpty())
            return "No encontre propiedades con esos criterios. Queres ajustar la busqueda?";

        StringBuilder sb = new StringBuilder("Encontre " + resultados.size() + " propiedad(es):\n\n");
        for (Propiedad p : resultados) {
            sb.append("ID #").append(p.getId()).append(" - ")
              .append(p.getTipo()).append(" en ").append(p.getZona())
              .append(" (").append(p.getOperacion()).append(")\n");
            sb.append("Precio: ").append(p.getMoneda()).append(" ").append(String.format("%.0f", p.getPrecio()));
            if (p.getHabitaciones() != null) sb.append(" | ").append(p.getHabitaciones()).append(" hab.");
            if (p.getBanos() != null) sb.append(" | ").append(p.getBanos()).append(" banos");
            if (p.getMetrosCuadrados() != null) sb.append(" | ").append(p.getMetrosCuadrados()).append(" m2");
            sb.append("\n").append(p.getDescripcion()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool(description = "Agenda una visita a una propiedad. " +
        "propiedadId: numero ID de la propiedad. " +
        "nombreCliente: nombre completo. " +
        "telefono: telefono de contacto. " +
        "fecha: formato YYYY-MM-DD. " +
        "hora: formato HH:mm.")
    public String agendarVisita(Long propiedadId, String nombreCliente,
                                String telefono, String fecha, String hora) {
        String tenantId = TenantContext.get();
        if (!propiedadRepository.existsById(propiedadId))
            return "No encontre la propiedad con ID #" + propiedadId + ". Verifica el numero.";

        boolean ocupado = visitaRepository.existsByTenantIdAndPropiedadIdAndFechaAndHora(
            tenantId, propiedadId, LocalDate.parse(fecha), hora);
        if (ocupado)
            return "Esa fecha y hora ya tiene una visita agendada para esa propiedad. Queres otro horario?";

        Visita visita = new Visita();
        visita.setTenantId(tenantId);
        visita.setPropiedadId(propiedadId);
        visita.setNombreCliente(nombreCliente);
        visita.setTelefono(telefono);
        visita.setFecha(LocalDate.parse(fecha));
        visita.setHora(hora);
        visitaRepository.save(visita);

        return "Visita confirmada para " + nombreCliente + " el " + fecha + " a las " + hora +
               ". Un asesor se contactara al " + telefono + " para coordinar los detalles.";
    }
}
