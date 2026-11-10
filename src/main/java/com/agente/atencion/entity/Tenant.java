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
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_tipo", length = 20)
    private TipoCodigo planTipo;
    @Column(length = 3)
    private String moneda;
}
