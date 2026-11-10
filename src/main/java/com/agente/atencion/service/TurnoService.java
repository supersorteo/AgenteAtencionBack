package com.agente.atencion.service;

import com.agente.atencion.dto.TurnoCrearRequest;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.Turno;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.TurnoRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class TurnoService {

    @Autowired private TurnoRepository turnoRepository;
    @Autowired private BarberoRepository barberoRepository;
    @Autowired private DisponibilidadService disponibilidadService;
    @Autowired private TenantClockService tenantClockService;
    @Autowired private TenantConfigService tenantConfigService;

    @Transactional
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public Turno crear(String tenantId, TurnoCrearRequest request) {
        Tenant tenant = tenantConfigService.getTenant(tenantId);
        if (tenant == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado.");
        if (SuscripcionService.estado(tenant) != SuscripcionService.EstadoAcceso.ACTIVO) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Barbería desactivada");
        if (request.fecha().isBefore(LocalDate.now(tenantClockService.clockFor(tenantId)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden crear reservas en fechas pasadas.");
        }
        Turno turno = new Turno();
        turno.setTenantId(tenantId);
        turno.setBarberoId(request.barberoId());
        turno.setServicio(request.servicio());
        turno.setFecha(request.fecha());
        turno.setHora(request.hora());
        turno.setPaciente(request.paciente());
        turno.setTelefono(request.telefono());
        turno.setEstado("RESERVADO");

        if (barberoRepository.bloquearParaReserva(tenantId, turno.getBarberoId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El barbero no está disponible para este negocio.");
        }
        int duracion = disponibilidadService.buscarDuracion(tenantId, turno.getServicio())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El servicio no existe."));
        if (!disponibilidadService.slotValido(tenantId, turno.getBarberoId(), turno.getFecha(), turno.getHora(), duracion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El horario elegido no es válido para este barbero.");
        }
        String horaFin = disponibilidadService.calcularHoraFin(turno.getHora(), duracion);
        turno.setHoraFin(horaFin);
        if (!disponibilidadService.validar(turno.getBarberoId(), turno.getFecha(), turno.getHora(), horaFin)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El horario ya no está disponible. Por favor elegí otro.");
        }
        return turnoRepository.save(turno);
    }

    @Transactional
    @CacheEvict(value = "disponibilidad", allEntries = true)
    public Turno reasignar(String tenantId, Long id, Long nuevoBarberoId) {
        Turno t = turnoRepository.findById(id)
            .filter(turno -> turno.getTenantId().equals(tenantId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (barberoRepository.bloquearParaReserva(tenantId, nuevoBarberoId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Barbero no válido para este tenant");
        }
        if (t.getHora() != null && t.getHoraFin() != null
                && !disponibilidadService.validar(nuevoBarberoId, t.getFecha(), t.getHora(), t.getHoraFin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El barbero seleccionado no está disponible en ese horario");
        }
        t.setBarberoId(nuevoBarberoId);
        return turnoRepository.save(t);
    }

    @CacheEvict(value = "disponibilidad", allEntries = true)
    public boolean eliminar(String tenantId, Long id) {
        return turnoRepository.findById(id)
            .filter(t -> t.getTenantId().equals(tenantId))
            .map(t -> { turnoRepository.delete(t); return true; })
            .orElse(false);
    }

    @CacheEvict(value = "disponibilidad", allEntries = true)
    public Optional<Turno> actualizarEstado(String tenantId, Long id, String estado, UsuarioAutenticado u) {
        return turnoRepository.findById(id)
            .filter(t -> {
                if (!t.getTenantId().equals(tenantId)) return false;
                if ("BARBERO".equals(u.rol())) {
                    return u.barberoId() != null && u.barberoId().equals(t.getBarberoId());
                }
                return "ADMIN".equals(u.rol()) && tenantId.equals(u.tenantId());
            })
            .map(t -> { t.setEstado(estado); return turnoRepository.save(t); });
    }
}
