package com.agente.atencion.service;

import com.agente.atencion.entity.DemoCredencial;
import com.agente.atencion.repository.DemoCredencialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoCredencialServiceTest {

    private DemoCredencialRepository repo;
    private DemoCredencialService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(DemoCredencialRepository.class);
        servicio = new DemoCredencialService(repo);
    }

    @Test
    void leeSoloLaFilaDeLaDemo() {
        DemoCredencial c = new DemoCredencial();
        c.setTenantId("barberia-demo");
        when(repo.findById("barberia-demo")).thenReturn(Optional.of(c));

        assertTrue(servicio.obtener().isPresent());
    }

    @Test
    void actualizarReemplazaUsuarioYContrasenaDeLaFilaExistente() {
        DemoCredencial c = new DemoCredencial();
        c.setTenantId("barberia-demo");
        c.setUsername("admin");
        c.setPassword("admin123");
        when(repo.findById("barberia-demo")).thenReturn(Optional.of(c));

        servicio.actualizar("admin", "NuevaClave456");

        ArgumentCaptor<DemoCredencial> guardada = ArgumentCaptor.forClass(DemoCredencial.class);
        verify(repo).save(guardada.capture());
        assertEquals("NuevaClave456", guardada.getValue().getPassword());
        assertEquals("barberia-demo", guardada.getValue().getTenantId());
    }

    @Test
    void siNoExisteLaFilaLaCreaParaLaDemo() {
        when(repo.findById("barberia-demo")).thenReturn(Optional.empty());

        servicio.actualizar("admin", "NuevaClave456");

        ArgumentCaptor<DemoCredencial> guardada = ArgumentCaptor.forClass(DemoCredencial.class);
        verify(repo).save(guardada.capture());
        assertEquals("barberia-demo", guardada.getValue().getTenantId());
        assertEquals("admin", guardada.getValue().getUsername());
    }
}
