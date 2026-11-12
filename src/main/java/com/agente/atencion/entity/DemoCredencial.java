package com.agente.atencion.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "demo_credenciales")
@Data
public class DemoCredencial {
    @Id
    private String tenantId;
    private String username;
    private String password;
}
