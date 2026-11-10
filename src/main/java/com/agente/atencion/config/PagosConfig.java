package com.agente.atencion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MercadoPagoProperties.class, PayPalProperties.class, PagosProperties.class})
public class PagosConfig {}
