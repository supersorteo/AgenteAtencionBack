package com.agente.atencion.entity;

import jakarta.persistence.*;
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
    private Integer diaSemana;
    private String horaInicio;
    private String horaFin;
}
