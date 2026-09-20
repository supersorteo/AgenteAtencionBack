package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bloqueos_horario")
@Data
public class BloqueoHorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long barberoId;
    private String tenantId;
    private LocalDate fecha;
    private String horaInicio;
    private String horaFin;
    private String motivo;
    private LocalDateTime creadoEn = LocalDateTime.now();
}
