package com.agente.atencion.dto;

import java.time.LocalDate;

public record TurnoCrearRequest(
    Long barberoId,
    String servicio,
    LocalDate fecha,
    String hora,
    String paciente,
    String telefono
) {}
