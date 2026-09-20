package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "visitas")
@Data
public class Visita {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private Long propiedadId;
    private String nombreCliente;
    private String telefono;
    private LocalDate fecha;
    private String hora;
    private String estado = "PENDIENTE";
    private LocalDateTime creadoEn = LocalDateTime.now();
}
