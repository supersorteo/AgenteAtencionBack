package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tenants")
@Data
public class Tenant {
    @Id
    private String id;
    private String nombre;
    @Column(columnDefinition = "TEXT")
    private String contexto;
    private boolean activo = true;
}
