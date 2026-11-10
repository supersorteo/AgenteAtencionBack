package com.agente.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TurnoCrearRequest(
    @NotNull Long barberoId,
    @NotBlank @Size(max = 100) String servicio,
    @NotNull LocalDate fecha,
    @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "formato HH:mm") String hora,
    @NotBlank @Size(max = 100) String paciente,
    @Size(max = 30) String telefono
) {}
