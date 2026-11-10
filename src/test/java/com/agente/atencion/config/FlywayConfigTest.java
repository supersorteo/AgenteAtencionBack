package com.agente.atencion.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayConfigTest {

    @Test
    void entityManagerFactoryPasaADependerDeFlyway() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerBeanDefinition("entityManagerFactory", new RootBeanDefinition(Object.class));

        FlywayConfig.forzarFlywayAntesDeJpa(bf);

        assertTrue(Arrays.asList(bf.getBeanDefinition("entityManagerFactory").getDependsOn()).contains("flyway"));
    }

    @Test
    void conservaLasDependenciasExistentesSinDuplicar() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        RootBeanDefinition emf = new RootBeanDefinition(Object.class);
        emf.setDependsOn("otroBean", "flyway");
        bf.registerBeanDefinition("entityManagerFactory", emf);

        FlywayConfig.forzarFlywayAntesDeJpa(bf);

        assertEquals(Arrays.asList("otroBean", "flyway"),
            Arrays.asList(bf.getBeanDefinition("entityManagerFactory").getDependsOn()));
    }

    @Test
    void toleraDependenciasRepetidasSinFallar() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        RootBeanDefinition emf = new RootBeanDefinition(Object.class);
        emf.setDependsOn("otroBean", "otroBean");
        bf.registerBeanDefinition("entityManagerFactory", emf);

        FlywayConfig.forzarFlywayAntesDeJpa(bf);

        assertEquals(Arrays.asList("otroBean", "flyway"),
            Arrays.asList(bf.getBeanDefinition("entityManagerFactory").getDependsOn()));
    }

    @Test
    void sinEntityManagerFactoryNoHaceNada() {
        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        bf.registerBeanDefinition("otro", new RootBeanDefinition(Object.class));

        FlywayConfig.forzarFlywayAntesDeJpa(bf);

        assertNull(bf.getBeanDefinition("otro").getDependsOn());
    }
}
