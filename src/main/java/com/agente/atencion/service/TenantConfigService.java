package com.agente.atencion.service;

import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TenantConfigService {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private NegocioConfigRepository negocioConfigRepository;

    @Cacheable(value = "tenants", unless = "#result == null")
    public Tenant getTenant(String tenantId) {
        return tenantRepository.findById(tenantId).orElse(null);
    }

    @Cacheable(value = "negocioConfigs", unless = "#result == null")
    public NegocioConfig getNegocioConfig(String tenantId) {
        return negocioConfigRepository.findById(tenantId).orElse(null);
    }

    @CacheEvict(value = "tenants", key = "#tenantId")
    public void evictTenant(String tenantId) {}

    @CacheEvict(value = "negocioConfigs", key = "#tenantId")
    public void evictNegocioConfig(String tenantId) {}
}
