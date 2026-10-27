package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "plan_precios")
@Data
public class PlanPrecio {
    @Id
    @Enumerated(EnumType.STRING)
    private TipoCodigo tipo;
    private BigDecimal precioUsd;
    private BigDecimal precioUyu;
    private BigDecimal precioArs;
}
