package com.agente.atencion.service;

import com.agente.atencion.entity.PlanPrecio;
import com.agente.atencion.entity.TipoCodigo;
import com.agente.atencion.repository.PlanPrecioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PlanPrecioInitializer {

    private final PlanPrecioRepository repo;

    public PlanPrecioInitializer(PlanPrecioRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void seedDefaults() {
        if (repo.count() > 0) return;

        Object[][] defaults = {
            { TipoCodigo.PLAN_1M,  new BigDecimal("20"),  new BigDecimal("800"),  new BigDecimal("31000") },
            { TipoCodigo.PLAN_3M,  new BigDecimal("50"),  new BigDecimal("2000"), new BigDecimal("77500") },
            { TipoCodigo.PLAN_6M,  new BigDecimal("90"),  new BigDecimal("3600"), new BigDecimal("139500") },
            { TipoCodigo.PLAN_12M, new BigDecimal("150"), new BigDecimal("6000"), new BigDecimal("232500") },
        };

        for (Object[] row : defaults) {
            PlanPrecio p = new PlanPrecio();
            p.setTipo((TipoCodigo) row[0]);
            p.setPrecioUsd((BigDecimal) row[1]);
            p.setPrecioUyu((BigDecimal) row[2]);
            p.setPrecioArs((BigDecimal) row[3]);
            repo.save(p);
        }
    }
}
