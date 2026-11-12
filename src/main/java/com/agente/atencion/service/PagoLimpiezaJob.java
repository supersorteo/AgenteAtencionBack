package com.agente.atencion.service;

import com.agente.atencion.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class PagoLimpiezaJob {

    private static final Logger log = LoggerFactory.getLogger(PagoLimpiezaJob.class);
    // Las órdenes de MP/PayPal caducan mucho antes; pasados 30 días una orden pendiente ya no se puede pagar
    static final int DIAS_DE_GRACIA = 30;

    private final PagoRepository pagos;
    private final Clock reloj;

    @org.springframework.beans.factory.annotation.Autowired
    public PagoLimpiezaJob(PagoRepository pagos) {
        this(pagos, Clock.systemUTC());
    }

    PagoLimpiezaJob(PagoRepository pagos, Clock reloj) {
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "UTC")
    @Transactional
    public int limpiar() {
        int borradas = pagos.eliminarPendientesAnterioresA(LocalDateTime.now(reloj).minusDays(DIAS_DE_GRACIA));
        if (borradas > 0) log.info("[PagoLimpiezaJob] eliminadas {} órdenes pendientes de más de {} días", borradas, DIAS_DE_GRACIA);
        return borradas;
    }
}
