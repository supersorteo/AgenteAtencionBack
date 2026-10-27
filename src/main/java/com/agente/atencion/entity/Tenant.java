package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

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
    @Column(name = "tour_visto")
    private Boolean tourVisto = false;
    private LocalDate fechaVencimientoSuscripcion;
    private LocalDate fechaRegistro;
}
