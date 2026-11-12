package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "formato HH:mm")
    private String horaInicio;
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "formato HH:mm")
    private String horaFin;
    @Size(max = 255)
    private String motivo;
    private LocalDateTime creadoEn = LocalDateTime.now();
}
