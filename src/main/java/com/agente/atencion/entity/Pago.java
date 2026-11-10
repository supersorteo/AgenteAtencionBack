package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, unique = true)
    private String referencia;

    @Column(name = "tenant_id")
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProveedorPago proveedor;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_plan", nullable = false, length = 20)
    private TipoCodigo tipoPlan;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(name = "codigo_generado")
    private String codigoGenerado;

    @Column(name = "creado_at", nullable = false)
    private LocalDateTime creadoAt;

    @Column(name = "confirmado_at")
    private LocalDateTime confirmadoAt;

    @Column(columnDefinition = "TEXT")
    private String payload;
}
