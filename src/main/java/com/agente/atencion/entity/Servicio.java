package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "servicios")
@Data
public class Servicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String nombre;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    private Double precio;
    private Integer duracionMinutos;
    private String emoji = "✂️";
    private String categoria;
    private String imagenUrl;
    private String imagenUrl2;
    private String imagenUrl3;
    private Boolean activo = true;
}
