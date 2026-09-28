package com.agente.atencion.repository;

import com.agente.atencion.entity.CodigoInvitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CodigoInvitacionRepository extends JpaRepository<CodigoInvitacion, String> {
    List<CodigoInvitacion> findAllByOrderByCreadoAtDesc();
    long countByUsado(boolean usado);
}
