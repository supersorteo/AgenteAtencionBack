package com.agente.atencion.service;

import com.agente.atencion.config.CacheConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConfigServiceCacheTest {

    @Configuration
    @EnableCaching
    @Import(CacheConfig.class)
    static class Config {
        @Bean TenantRepository tenantRepository() { return Mockito.mock(TenantRepository.class); }
        @Bean NegocioConfigRepository negocioConfigRepository() { return Mockito.mock(NegocioConfigRepository.class); }
        @Bean TenantConfigService tenantConfigService() { return new TenantConfigService(); }
    }

    private AnnotationConfigApplicationContext contexto;
    private TenantRepository repo;
    private TenantConfigService servicio;

    @BeforeEach
    void setUp() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        repo = contexto.getBean(TenantRepository.class);
        servicio = contexto.getBean(TenantConfigService.class);
    }

    @AfterEach
    void tearDown() {
        contexto.close();
    }

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setId("nueva-barberia");
        return t;
    }

    @Test
    void unNegocioQueTodaviaNoExisteNoQuedaRecordadoComoInexistente() {
        when(repo.findById("nueva-barberia")).thenReturn(Optional.empty());
        assertNull(servicio.getTenant("nueva-barberia"));

        Tenant creado = tenant();
        when(repo.findById("nueva-barberia")).thenReturn(Optional.of(creado));

        assertSame(creado, servicio.getTenant("nueva-barberia"));
    }

    @Test
    void unNegocioExistenteSeLeeUnaSolaVez() {
        when(repo.findById("nueva-barberia")).thenReturn(Optional.of(tenant()));

        servicio.getTenant("nueva-barberia");
        servicio.getTenant("nueva-barberia");

        verify(repo, times(1)).findById("nueva-barberia");
    }

    @Test
    void vaciarElNegocioForzaUnaNuevaLectura() {
        Tenant original = tenant();
        Tenant desactivado = tenant();
        desactivado.setActivo(false);
        when(repo.findById("nueva-barberia")).thenReturn(Optional.of(original)).thenReturn(Optional.of(desactivado));

        assertEquals(true, servicio.getTenant("nueva-barberia").isActivo());
        servicio.evictTenant("nueva-barberia");

        assertEquals(false, servicio.getTenant("nueva-barberia").isActivo());
    }
}
