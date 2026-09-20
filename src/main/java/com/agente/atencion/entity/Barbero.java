package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "barberos")
@Data
public class Barbero {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String nombre;
    private String especialidad;
    private String foto;
    private Boolean activo = true;
}
