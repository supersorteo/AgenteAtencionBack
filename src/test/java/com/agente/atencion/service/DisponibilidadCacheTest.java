package com.agente.atencion.service;

import com.agente.atencion.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisponibilidadCacheTest {

    private static final String A_FECHA1 = "barberia-a_2026-11-12_Corte_null";
    private static final String A_FECHA2 = "barberia-a_2026-11-13_Barba_7";
    private static final String B_FECHA1 = "barberia-b_2026-11-12_Corte_null";
    private static final String AB_FECHA1 = "barberia-a-b_2026-11-12_Corte_null";

    private Cache disponibilidad;
    private DisponibilidadCache cache;

    @BeforeEach
    void setUp() {
        CacheManager manager = new CacheConfig().cacheManager();
        disponibilidad = manager.getCache(CacheConfig.DISPONIBILIDAD);
        cache = new DisponibilidadCache(manager);
        for (String clave : new String[] {A_FECHA1, A_FECHA2, B_FECHA1, AB_FECHA1}) disponibilidad.put(clave, "slots");
    }

    @Test
    void vaciarUnNegocioNoTocaLosDemas() {
        cache.evictTenant("barberia-a");

        assertNull(disponibilidad.get(A_FECHA1));
        assertNull(disponibilidad.get(A_FECHA2));
        assertNotNull(disponibilidad.get(B_FECHA1));
    }

    @Test
    void unSlugQueEsPrefijoDeOtroNoBorraAlOtro() {
        cache.evictTenant("barberia-a");

        assertNotNull(disponibilidad.get(AB_FECHA1));
    }

    @Test
    void sinNegocioNoHaceNada() {
        cache.evictTenant(null);

        assertNotNull(disponibilidad.get(A_FECHA1));
        assertNotNull(disponibilidad.get(B_FECHA1));
    }

    @Test
    void dentroDeUnaTransaccionSoloSeVaciaDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictTenant("barberia-a");
            assertNotNull(disponibilidad.get(A_FECHA1), "no debe vaciarse antes del commit");

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();

            assertNull(disponibilidad.get(A_FECHA1));
            assertNotNull(disponibilidad.get(B_FECHA1));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void siLaTransaccionHaceRollbackLaCacheQuedaIntacta() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictTenant("barberia-a");
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertNotNull(disponibilidad.get(A_FECHA1));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
