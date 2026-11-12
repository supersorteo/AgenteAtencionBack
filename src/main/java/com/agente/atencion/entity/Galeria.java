package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "galeria")
@Data
public class Galeria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    @Size(max = 120)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String descripcion;
    @Size(max = 255)
    private String imagenUrl;
    @Size(max = 80)
    private String categoria;
    private Long servicioId;
    private Boolean activo = true;
}
