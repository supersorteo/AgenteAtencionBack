package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "propiedades")
@Data
public class Propiedad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String tipo;
    private String operacion;
    private String zona;
    private Double precio;
    private String moneda = "USD";
    private Integer habitaciones;
    private Integer banos;
    private Double metrosCuadrados;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    private Boolean disponible = true;
}
