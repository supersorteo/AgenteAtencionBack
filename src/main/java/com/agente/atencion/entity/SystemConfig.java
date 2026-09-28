package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "system_config")
@Data
public class SystemConfig {
    @Id
    private String clave;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String valor;
}
