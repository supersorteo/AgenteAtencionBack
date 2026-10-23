package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "turnos", indexes = {
    @Index(name = "idx_turnos_tenant_fecha", columnList = "tenantId, fecha"),
    @Index(name = "idx_turnos_tenant_barbero", columnList = "tenantId, barberoId"),
    @Index(name = "idx_turnos_tenant_estado", columnList = "tenantId, estado")
})
@Data
public class Turno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String paciente;
    private String telefono;
    private String servicio;
    private Long barberoId;
    private LocalDate fecha;
    private String hora;
    private String horaFin;
    private String estado = "RESERVADO";
    private LocalDateTime creadoEn = LocalDateTime.now();
}
