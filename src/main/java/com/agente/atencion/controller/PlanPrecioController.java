package com.agente.atencion.controller;

import com.agente.atencion.entity.PlanPrecio;
import com.agente.atencion.repository.PlanPrecioRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plan-precios")
public class PlanPrecioController {

    private final PlanPrecioRepository repo;

    public PlanPrecioController(PlanPrecioRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<PlanPrecio> getAll() {
        return repo.findAll();
    }
}
