package com.agente.atencion.controller;

import com.agente.atencion.service.DisponibilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/disponibilidad")
public class DisponibilidadController {

    @Autowired private DisponibilidadService disponibilidadService;

    @GetMapping("/{tenantId}")
    public List<DisponibilidadService.SlotDisponible> consultar(
            @PathVariable String tenantId,
            @RequestParam String fecha,
            @RequestParam String servicio,
            @RequestParam(required = false) Long barberoId) {
        return disponibilidadService.calcular(tenantId, LocalDate.parse(fecha), servicio, barberoId);
    }
}
