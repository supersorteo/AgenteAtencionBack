package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "barberos")
@Data
public class Barbero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    @Size(max = 100)
    private String nombre;
    @Size(max = 150)
    private String especialidad;
    @Size(max = 255)
    private String foto;
    private Boolean activo = true;
}
