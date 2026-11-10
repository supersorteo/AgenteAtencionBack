package com.agente.atencion.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String TENANTS = "tenants";
    public static final String NEGOCIO_CONFIGS = "negocioConfigs";
    public static final String BARBEROS = "barberos";
    public static final String SERVICIOS = "servicios";
    public static final String DISPONIBILIDAD = "disponibilidad";
    public static final String DASHBOARD = "dashboard";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(1_000).expireAfterWrite(Duration.ofMinutes(5)).recordStats());
        manager.registerCustomCache(TENANTS, construir(5_000, Duration.ofMinutes(2)));
        manager.registerCustomCache(NEGOCIO_CONFIGS, construir(5_000, Duration.ofMinutes(5)));
        manager.registerCustomCache(BARBEROS, construir(5_000, Duration.ofMinutes(5)));
        manager.registerCustomCache(SERVICIOS, construir(5_000, Duration.ofMinutes(5)));
        manager.registerCustomCache(DISPONIBILIDAD, construir(20_000, Duration.ofMinutes(2)));
        manager.registerCustomCache(DASHBOARD, construir(10_000, Duration.ofSeconds(15)));
        return manager;
    }

    private static com.github.benmanes.caffeine.cache.Cache<Object, Object> construir(long maximo, Duration ttl) {
        return Caffeine.newBuilder().maximumSize(maximo).expireAfterWrite(ttl).recordStats().build();
    }
}
