package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "galeria")
@Data
public class Galeria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    private String imagenUrl;
    private String categoria;
    private Long servicioId;
    private Boolean activo = true;
}
