package com.agente.atencion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class NegocioClockConfig {
    @Bean
    public Clock negocioClock(@Value("${app.negocio.time-zone:America/Montevideo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
