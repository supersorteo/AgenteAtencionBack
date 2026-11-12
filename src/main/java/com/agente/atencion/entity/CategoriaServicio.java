package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "categoria_servicio")
@Data
public class CategoriaServicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    @Size(max = 80)
    private String nombre;
    @Size(max = 16)
    private String emoji;
    @Size(max = 255)
    private String imagenUrl;
    @Min(0) @Max(10000)
    private int orden;
    private boolean activo = true;
}
