package com.agente.atencion.controller;

import com.agente.atencion.entity.Barbero;
import com.agente.atencion.entity.BloqueoHorario;
import com.agente.atencion.entity.Servicio;
import com.agente.atencion.repository.BarberoRepository;
import com.agente.atencion.repository.BloqueoHorarioRepository;
import com.agente.atencion.repository.HorarioBarberoRepository;
import com.agente.atencion.repository.ServicioRepository;
import com.agente.atencion.security.UsuarioAutenticado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AislamientoTenantTest {

    private static final String A = "barberia-a";
    private static final String B = "barberia-b";

    @Mock ServicioRepository servicioRepository;
    @Mock BloqueoHorarioRepository bloqueoRepository;
    @Mock BarberoRepository barberoRepository;
    @Mock HorarioBarberoRepository horarioRepository;
    @InjectMocks ServicioController servicioController;
    @InjectMocks BloqueoController bloqueoController;
    @InjectMocks BarberoController barberoController;

    private Authentication token(UsuarioAutenticado u) {
        return new UsernamePasswordAuthenticationToken(u, null, List.of());
    }

    private Barbero barbero(long id, String tenantId) {
        Barbero b = new Barbero();
        b.setId(id);
        b.setTenantId(tenantId);
        return b;
    }

    @Test
    void crearServicio_ignoraElIdDelBodyYNoPisaFilasAjenas() {
        Servicio entrante = new Servicio();
        entrante.setId(99L);
        entrante.setNombre("Corte");
        when(servicioRepository.save(any(Servicio.class))).thenAnswer(i -> i.getArgument(0));

        servicioController.crear(A, entrante, token(new UsuarioAutenticado("admin", "ADMIN", A, null)));

        ArgumentCaptor<Servicio> captor = ArgumentCaptor.forClass(Servicio.class);
        verify(servicioRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals(A, captor.getValue().getTenantId());
    }

    @Test
    void listarBloqueos_adminDeOtroTenant_403() {
        ResponseEntity<List<BloqueoHorario>> r = bloqueoController.listar(A, null,
            token(new UsuarioAutenticado("admin", "ADMIN", B, null)));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        verify(bloqueoRepository, never()).findByTenantId(any());
    }

    @Test
    void listarBloqueos_barberoDelMismoTenant_403() {
        ResponseEntity<List<BloqueoHorario>> r = bloqueoController.listar(A, null,
            token(new UsuarioAutenticado("barbero", "BARBERO", A, 5L)));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void crearBloqueo_conBarberoDeOtroTenant_400() {
        BloqueoHorario bloqueo = new BloqueoHorario();
        bloqueo.setBarberoId(7L);
        when(barberoRepository.findById(7L)).thenReturn(Optional.of(barbero(7L, B)));

        ResponseEntity<?> r = bloqueoController.crear(A, bloqueo,
            token(new UsuarioAutenticado("admin", "ADMIN", A, null)));

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearBloqueo_propio_ignoraIdDelBody() {
        BloqueoHorario bloqueo = new BloqueoHorario();
        bloqueo.setId(50L);
        bloqueo.setBarberoId(7L);
        when(barberoRepository.findById(7L)).thenReturn(Optional.of(barbero(7L, A)));
        when(bloqueoRepository.save(any(BloqueoHorario.class))).thenAnswer(i -> i.getArgument(0));

        bloqueoController.crear(A, bloqueo, token(new UsuarioAutenticado("admin", "ADMIN", A, null)));

        ArgumentCaptor<BloqueoHorario> captor = ArgumentCaptor.forClass(BloqueoHorario.class);
        verify(bloqueoRepository).save(captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals(A, captor.getValue().getTenantId());
    }

    @Test
    void getHorario_barberoDeOtroTenant_404() {
        when(barberoRepository.findById(7L)).thenReturn(Optional.of(barbero(7L, B)));
        assertEquals(HttpStatus.NOT_FOUND, barberoController.getHorario(A, 7L).getStatusCode());
        verify(horarioRepository, never()).findByBarberoId(any());
    }

    @Test
    void getHorario_barberoPropio_200() {
        when(barberoRepository.findById(7L)).thenReturn(Optional.of(barbero(7L, A)));
        when(horarioRepository.findByBarberoId(7L)).thenReturn(List.of());
        assertEquals(HttpStatus.OK, barberoController.getHorario(A, 7L).getStatusCode());
    }
}
