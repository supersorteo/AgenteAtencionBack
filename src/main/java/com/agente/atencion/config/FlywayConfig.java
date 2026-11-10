package com.agente.atencion.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@Profile("!test")
public class FlywayConfig {

    static final String FLYWAY_BEAN = "flyway";

    @Bean(name = FLYWAY_BEAN, initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load();
    }

    // ddl-auto=validate se ejecuta al crear el EntityManagerFactory: Flyway debe migrar antes
    @Bean
    static BeanFactoryPostProcessor flywayAntesQueJpa() {
        return FlywayConfig::forzarFlywayAntesDeJpa;
    }

    static void forzarFlywayAntesDeJpa(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")) return;
        BeanDefinition definicion = beanFactory.getBeanDefinition("entityManagerFactory");
        Set<String> dependencias = new LinkedHashSet<>();
        if (definicion.getDependsOn() != null) Collections.addAll(dependencias, definicion.getDependsOn());
        dependencias.add(FLYWAY_BEAN);
        definicion.setDependsOn(dependencias.toArray(String[]::new));
    }
}
