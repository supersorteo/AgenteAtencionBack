package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "negocio_config")
@Data
public class NegocioConfig {
    @Id
    private String tenantId;

    // Identidad
    private String nombre;
    private String tagline;
    private String heroTitulo1;
    private String heroTitulo2;
    @Column(columnDefinition = "TEXT")
    private String heroDesc;
    @Column(columnDefinition = "TEXT")
    private String footerDesc;

    // Contacto
    private String direccion;
    private String telefono;
    private String email;
    private String whatsapp;       // número con código de país, solo dígitos
    private String instagramHandle; // username sin @

    // Zona horaria del negocio (IANA, ej: "America/Montevideo")
    @Column(name = "time_zone")
    private String timeZone;

    // Horarios
    private String horario1;
    private String horario2;
    private String horario3;

    // Testimonio 1
    private String t1Nombre;
    private String t1Iniciales;
    private String t1Servicio;
    @Column(columnDefinition = "TEXT")
    private String t1Texto;

    // Testimonio 2
    private String t2Nombre;
    private String t2Iniciales;
    private String t2Servicio;
    @Column(columnDefinition = "TEXT")
    private String t2Texto;

    // Testimonio 3
    private String t3Nombre;
    private String t3Iniciales;
    private String t3Servicio;
    @Column(columnDefinition = "TEXT")
    private String t3Texto;
}
