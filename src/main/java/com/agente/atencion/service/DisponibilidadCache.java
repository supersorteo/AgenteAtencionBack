package com.agente.atencion.service;

import com.agente.atencion.config.CacheConfig;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DisponibilidadCache {

    private final CacheManager cacheManager;

    public DisponibilidadCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Las claves de la caché empiezan con "<tenantId>_"; un slug nunca contiene "_"
    public void evictTenant(String tenantId) {
        if (tenantId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    limpiar(tenantId);
                }
            });
        } else {
            limpiar(tenantId);
        }
    }

    void limpiar(String tenantId) {
        String prefijo = tenantId + "_";
        for (String nombre : new String[] {CacheConfig.DISPONIBILIDAD, CacheConfig.DASHBOARD}) {
            if (cacheManager.getCache(nombre) instanceof CaffeineCache caffeine) {
                caffeine.getNativeCache().asMap().keySet().removeIf(k -> k instanceof String s && s.startsWith(prefijo));
            }
        }
    }
}
