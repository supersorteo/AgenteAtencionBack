package com.agente.atencion.repository;

import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.TipoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CodigoInvitacionRepository extends JpaRepository<CodigoInvitacion, String> {
    List<CodigoInvitacion> findAllByOrderByCreadoAtDesc();
    List<CodigoInvitacion> findAllByTipoOrderByCreadoAtDesc(TipoCodigo tipo);
    long countByUsado(boolean usado);
    java.util.List<CodigoInvitacion> findAllByUsadoPorUsername(String username);
    long deleteByTipoIsNull();
}
