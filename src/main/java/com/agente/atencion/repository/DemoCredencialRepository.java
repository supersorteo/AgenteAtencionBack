package com.agente.atencion.repository;

import com.agente.atencion.entity.DemoCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoCredencialRepository extends JpaRepository<DemoCredencial, String> {
}
