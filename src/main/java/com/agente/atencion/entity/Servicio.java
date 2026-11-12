package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "servicios")
@Data
public class Servicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    @Size(max = 100)
    private String nombre;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String descripcion;
    @DecimalMin("0") @DecimalMax("100000000")
    private Double precio;
    @Min(1) @Max(1440)
    private Integer duracionMinutos;
    @Size(max = 16)
    private String emoji = "✂️";
    @Size(max = 80)
    private String categoria;
    @Size(max = 255)
    private String imagenUrl;
    @Size(max = 255)
    private String imagenUrl2;
    @Size(max = 255)
    private String imagenUrl3;
    private Boolean activo = true;
}
