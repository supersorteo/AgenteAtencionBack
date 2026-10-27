package com.agente.atencion.repository;

import com.agente.atencion.entity.PlanPrecio;
import com.agente.atencion.entity.TipoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPrecioRepository extends JpaRepository<PlanPrecio, TipoCodigo> {}
