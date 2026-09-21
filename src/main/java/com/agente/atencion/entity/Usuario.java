package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity @Table(name = "usuarios") @Data
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String rol; // ADMIN | BARBERO

    private Long barberoId; // solo para rol BARBERO

    private boolean activo = true;
}
