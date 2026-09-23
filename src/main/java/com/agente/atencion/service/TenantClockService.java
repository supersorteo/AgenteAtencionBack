package com.agente.atencion.service;

import com.agente.atencion.repository.NegocioConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantClockService {

    private static final String DEFAULT_ZONE = "America/Montevideo";

    @Autowired private NegocioConfigRepository configRepository;

    private final ConcurrentHashMap<String, Clock> cache = new ConcurrentHashMap<>();

    public Clock clockFor(String tenantId) {
        return cache.computeIfAbsent(tenantId, this::loadClock);
    }

    public void invalidate(String tenantId) {
        cache.remove(tenantId);
    }

    private Clock loadClock(String tenantId) {
        String zone = configRepository.findById(tenantId)
            .map(c -> c.getTimeZone())
            .filter(z -> z != null && !z.isBlank())
            .orElse(DEFAULT_ZONE);
        try {
            return Clock.system(ZoneId.of(zone));
        } catch (DateTimeException e) {
            return Clock.system(ZoneId.of(DEFAULT_ZONE));
        }
    }
}
