package com.agente.atencion.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity @Table(name = "usuarios") @Data
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String rol; // ADMIN | BARBERO

    private Long barberoId; // solo para rol BARBERO

    private boolean activo = true;

    @Column(name = "password_changed_at", columnDefinition = "TIMESTAMP")
    private LocalDateTime passwordChangedAt;
}
