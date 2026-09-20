package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "turnos")
@Data
public class Turno {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String paciente;
    private String telefono;
    private String servicio;
    private LocalDate fecha;
    private String hora;
    private String estado = "RESERVADO";
    private LocalDateTime creadoEn = LocalDateTime.now();
}
