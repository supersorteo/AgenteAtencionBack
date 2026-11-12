package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "horarios_barbero")
@Data
public class HorarioBarbero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long barberoId;
    // 1=Lunes, 2=Martes, 3=Miercoles, 4=Jueves, 5=Viernes, 6=Sabado, 7=Domingo
    @Min(1) @Max(7)
    private Integer diaSemana;
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "formato HH:mm")
    private String horaInicio;
    @Pattern(regexp = "^([01]?\\d|2[0-3]):[0-5]\\d$", message = "formato HH:mm")
    private String horaFin;
}
