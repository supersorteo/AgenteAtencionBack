package com.agente.atencion.dto;

import com.agente.atencion.entity.TipoCodigo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerarCodigosRequest(
    @Min(1) @Max(50) int cantidad,
    @NotNull TipoCodigo tipo
) {}
