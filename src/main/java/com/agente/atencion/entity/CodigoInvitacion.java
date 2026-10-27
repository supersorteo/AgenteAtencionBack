package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "codigos_invitacion")
@Data
public class CodigoInvitacion {
    @Id
    private String codigo;
    @Enumerated(EnumType.STRING)
    private TipoCodigo tipo;
    private boolean usado = false;
    private String usadoPorUsername;
    private LocalDateTime usadoAt;
    private LocalDate fechaVencimiento;
    private LocalDateTime creadoAt;
}
