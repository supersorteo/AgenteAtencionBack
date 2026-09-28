package com.agente.atencion.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "codigos_invitacion")
@Data
public class CodigoInvitacion {
    @Id
    private String codigo;
    private boolean usado = false;
    private String usadoPorUsername;
    private LocalDateTime usadoAt;
    private LocalDateTime creadoAt;
}
