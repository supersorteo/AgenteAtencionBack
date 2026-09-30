package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "categoria_servicio")
@Data
public class CategoriaServicio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String nombre;
    private String emoji;
    private int orden;
    private boolean activo = true;
}
