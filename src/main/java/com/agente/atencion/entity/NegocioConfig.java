package com.agente.atencion.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Entity
@Table(name = "negocio_config")
@Data
public class NegocioConfig {
    @Id
    private String tenantId;

    // Identidad
    @Size(max = 255)
    private String nombre;
    @Size(max = 255)
    private String tagline;
    @Size(max = 255)
    private String heroTitulo1;
    @Size(max = 255)
    private String heroTitulo2;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String heroDesc;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String footerDesc;

    // Contacto
    @Size(max = 255)
    private String direccion;
    @Size(max = 30)
    private String telefono;
    @Size(max = 120)
    private String email;
    @Size(max = 20)
    private String whatsapp;       // número con código de país, solo dígitos
    @Size(max = 255)
    private String instagramHandle; // username sin @

    // Zona horaria del negocio (IANA, ej: "America/Montevideo")
    @Column(name = "time_zone")
    @Size(max = 64)
    private String timeZone;

    // Personalización visual por tenant
    @Size(max = 64)
    private String colorPrimario;   // hex: "#c9a84c"
    @Size(max = 64)
    private String colorFondo;      // hex: fondo base de página
    @Size(max = 64)
    private String colorTexto;      // hex: texto principal
    @Size(max = 255)
    private String logoUrl;         // Cloudinary URL del logo
    @Size(max = 255)
    private String heroBgUrl;       // URL imagen de fondo del hero en la landing
    @Size(max = 8)
    private String moneda;          // "UYU", "ARS", "USD", "CLP", "COP", "MXN", "PEN"
    @Size(max = 4)
    private String paisCodigo;      // "UY", "AR", "US" — prefijo para WhatsApp

    // Horarios
    @Size(max = 255)
    private String horario1;
    @Size(max = 255)
    private String horario2;
    @Size(max = 255)
    private String horario3;

    // Testimonio 1
    @Size(max = 255)
    private String t1Nombre;
    @Size(max = 8)
    private String t1Iniciales;
    @Size(max = 255)
    private String t1Servicio;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String t1Texto;

    // Testimonio 2
    @Size(max = 255)
    private String t2Nombre;
    @Size(max = 8)
    private String t2Iniciales;
    @Size(max = 255)
    private String t2Servicio;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String t2Texto;

    // Testimonio 3
    @Size(max = 255)
    private String t3Nombre;
    @Size(max = 8)
    private String t3Iniciales;
    @Size(max = 255)
    private String t3Servicio;
    @Column(columnDefinition = "TEXT")
    @Size(max = 5000)
    private String t3Texto;
}
