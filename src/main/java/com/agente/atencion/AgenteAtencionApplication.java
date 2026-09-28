package com.agente.atencion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgenteAtencionApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgenteAtencionApplication.class, args);
    }
}
