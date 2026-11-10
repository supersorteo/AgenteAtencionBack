package com.agente.atencion.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheConfigTest {

    private final CacheManager manager = new CacheConfig().cacheManager();

    private long maximo(String cache) {
        CaffeineCache c = (CaffeineCache) manager.getCache(cache);
        return c.getNativeCache().policy().eviction().orElseThrow().getMaximum();
    }

    private long minutosDeVida(String cache) {
        CaffeineCache c = (CaffeineCache) manager.getCache(cache);
        return c.getNativeCache().policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.MINUTES);
    }

    @Test
    void lasCachesPorNegocioSoportanVariosMilesDeNegocios() {
        for (String nombre : new String[] {CacheConfig.TENANTS, CacheConfig.NEGOCIO_CONFIGS, CacheConfig.BARBEROS, CacheConfig.SERVICIOS}) {
            assertTrue(maximo(nombre) >= 5_000, nombre + " debe admitir al menos 5.000 negocios, tiene " + maximo(nombre));
        }
    }

    @Test
    void laCacheDeDisponibilidadTieneMasCapacidadYVidaCorta() {
        assertEquals(20_000, maximo(CacheConfig.DISPONIBILIDAD));
        assertEquals(2, minutosDeVida(CacheConfig.DISPONIBILIDAD));
    }

    @Test
    void elEstadoDelNegocioVenceRapidoParaQueUnaBajaManualSeNote() {
        assertEquals(2, minutosDeVida(CacheConfig.TENANTS));
        assertEquals(5, minutosDeVida(CacheConfig.NEGOCIO_CONFIGS));
    }

    @Test
    void cualquierOtraCacheTieneUnTopePropioYNoCreceSinLimite() {
        CaffeineCache otra = (CaffeineCache) manager.getCache("otra-cache");
        assertEquals(1_000, otra.getNativeCache().policy().eviction().orElseThrow().getMaximum());
    }
}
