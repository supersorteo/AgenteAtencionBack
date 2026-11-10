package com.agente.atencion.security;

import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.UsuarioRepository;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthCacheServiceTest {

    @Mock UsuarioRepository repo;

    private final long[] ahora = {0L};
    private final Ticker reloj = () -> ahora[0];
    private AuthCacheService cache;

    @BeforeEach
    void setUp() {
        cache = new AuthCacheService(repo, reloj);
    }

    private Usuario usuario(boolean activo, LocalDateTime cambio) {
        Usuario u = new Usuario();
        u.setUsername("ana");
        u.setActivo(activo);
        u.setPasswordChangedAt(cambio);
        return u;
    }

    @Test
    void laSegundaConsultaDentroDelTtlNoVaALaBase() {
        when(repo.findByUsername("ana")).thenReturn(Optional.of(usuario(true, null)));

        cache.usuario("ana");
        cache.usuario("ana");
        cache.usuario("ana");

        verify(repo, times(1)).findByUsername("ana");
    }

    @Test
    void guardaSoloLoNecesarioParaElFiltro() {
        LocalDateTime cambio = LocalDateTime.of(2026, 11, 10, 12, 0);
        when(repo.findByUsername("ana")).thenReturn(Optional.of(usuario(true, cambio)));

        AuthCacheService.UsuarioAuth datos = cache.usuario("ana").orElseThrow();

        assertTrue(datos.activo());
        assertEquals(cambio, datos.passwordChangedAt());
    }

    @Test
    void pasadoElTtlVuelveALeerParaQueUnCambioNoQuedeOculto() {
        when(repo.findByUsername("ana")).thenReturn(Optional.of(usuario(true, null)));
        cache.usuario("ana");

        ahora[0] += TimeUnit.SECONDS.toNanos(61);
        cache.usuario("ana");

        verify(repo, times(2)).findByUsername("ana");
    }

    @Test
    void vaciarUnUsuarioForzaLaRelecturaConLosDatosNuevos() {
        when(repo.findByUsername("ana"))
            .thenReturn(Optional.of(usuario(true, null)))
            .thenReturn(Optional.of(usuario(false, null)));

        assertTrue(cache.usuario("ana").orElseThrow().activo());
        cache.evict("ana");

        assertFalse(cache.usuario("ana").orElseThrow().activo());
    }

    @Test
    void vaciarTodoAfectaATodosLosUsuarios() {
        when(repo.findByUsername("ana")).thenReturn(Optional.of(usuario(true, null)));
        when(repo.findByUsername("luis")).thenReturn(Optional.of(usuario(true, null)));
        cache.usuario("ana");
        cache.usuario("luis");

        cache.evictAll();
        cache.usuario("ana");
        cache.usuario("luis");

        verify(repo, times(2)).findByUsername("ana");
        verify(repo, times(2)).findByUsername("luis");
    }

    @Test
    void unUsuarioInexistenteTambienSeRecuerdaDentroDelTtl() {
        when(repo.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertTrue(cache.usuario("fantasma").isEmpty());
        assertTrue(cache.usuario("fantasma").isEmpty());

        verify(repo, times(1)).findByUsername("fantasma");
    }

    @Test
    void sinNombreDeUsuarioNoConsultaNada() {
        assertTrue(cache.usuario(null).isEmpty());
        cache.evict(null);

        verify(repo, never()).findByUsername(org.mockito.ArgumentMatchers.any());
    }
}
