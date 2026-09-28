package com.agente.atencion.service;

import com.agente.atencion.dto.RegistroRequest;
import com.agente.atencion.dto.RegistroResponse;
import com.agente.atencion.entity.CodigoInvitacion;
import com.agente.atencion.entity.NegocioConfig;
import com.agente.atencion.entity.Tenant;
import com.agente.atencion.entity.Usuario;
import com.agente.atencion.repository.CodigoInvitacionRepository;
import com.agente.atencion.repository.NegocioConfigRepository;
import com.agente.atencion.repository.TenantRepository;
import com.agente.atencion.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock TenantRepository tenantRepo;
    @Mock NegocioConfigRepository configRepo;
    @Mock UsuarioRepository usuarioRepo;
    @Mock PasswordEncoder encoder;
    @Mock CodigoInvitacionRepository codigoRepo;
    @InjectMocks RegistroService sut;

    @BeforeEach
    void setUp() {
        CodigoInvitacion valido = new CodigoInvitacion();
        valido.setCodigo("BARBEROS2026");
        valido.setUsado(false);
        valido.setCreadoAt(LocalDateTime.now());
        // lenient: tests de generarSlug() no llaman registrar() y no usan estos stubs
        lenient().when(codigoRepo.findById("BARBEROS2026")).thenReturn(Optional.of(valido));
        lenient().when(codigoRepo.save(any(CodigoInvitacion.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ── generarSlug ──────────────────────────────────────────────────────────

    @Test
    void slugDesdeNombreSimple() {
        assertThat(sut.generarSlug("Barberia Norte")).isEqualTo("barberia-norte");
    }

    @Test
    void slugQuitaTildes() {
        assertThat(sut.generarSlug("Barbería El Señor")).isEqualTo("barberia-el-senor");
    }

    @Test
    void slugQuitaCaracteresEspeciales() {
        assertThat(sut.generarSlug("El Corte #1!")).isEqualTo("el-corte-1");
    }

    @Test
    void slugColapsaEspaciosMultiples() {
        assertThat(sut.generarSlug("Barber   Shop")).isEqualTo("barber-shop");
    }

    @Test
    void slugTruncaA40Chars() {
        String largo = "a".repeat(50);
        assertThat(sut.generarSlug(largo)).hasSize(40);
    }

    @Test
    void slugNombreSoloEspecialesDevuelveVacio() {
        assertThat(sut.generarSlug("!!! ###")).isEqualTo("");
    }

    // ── código de invitación ─────────────────────────────────────────────────

    @Test
    void registrarLanzaForbiddenSiCodigoInvalido() {
        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", "CODIGO-MALO");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Código de invitación");
    }

    @Test
    void registrarLanzaForbiddenSiCodigoNulo() {
        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", null);
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Código de invitación");
    }

    @Test
    void registrarLanzaForbiddenSiCodigoYaUsado() {
        CodigoInvitacion usado = new CodigoInvitacion();
        usado.setCodigo("USADO123");
        usado.setUsado(true);
        usado.setUsadoPorUsername("otro");
        when(codigoRepo.findById("USADO123")).thenReturn(Optional.of(usado));

        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", "USADO123");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("ya fue utilizado");
    }

    // ── validaciones ─────────────────────────────────────────────────────────

    @Test
    void registrarLanzaBadRequestSiNombreVacio() {
        var req = new RegistroRequest("", "Juan", "juan", "pass1234", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("nombre del negocio");
    }

    @Test
    void registrarLanzaBadRequestSiPasswordCorta() {
        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "abc", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("contraseña");
    }

    @Test
    void registrarLanzaBadRequestSiUsernameInvalido() {
        var req = new RegistroRequest("Mi Barberia", "Juan", "ju@an!", "pass1234", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("usuario");
    }

    @Test
    void registrarLanzaBadRequestSiSlugResultanteVacio() {
        var req = new RegistroRequest("!!! ###", "Juan", "juan", "pass1234", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("nombre del negocio");
    }

    @Test
    void registrarLanzaConflictSiSlugYaExiste() {
        when(tenantRepo.existsById("mi-barberia")).thenReturn(true);
        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("nombre");
    }

    @Test
    void registrarLanzaConflictSiUsernameYaExiste() {
        when(tenantRepo.existsById(anyString())).thenReturn(false);
        when(usuarioRepo.findByUsernameAndActivoTrue("juan")).thenReturn(Optional.of(new Usuario()));
        var req = new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", "BARBEROS2026");
        assertThatThrownBy(() -> sut.registrar(req))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("usuario");
    }

    @Test
    void registrarCreaEntidadesYDevuelveResponse() {
        when(tenantRepo.existsById(anyString())).thenReturn(false);
        when(usuarioRepo.findByUsernameAndActivoTrue(anyString())).thenReturn(Optional.empty());
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(tenantRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(usuarioRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        RegistroResponse res = sut.registrar(
            new RegistroRequest("Mi Barberia", "Juan", "juan", "pass1234", "BARBEROS2026")
        );

        assertThat(res.slug()).isEqualTo("mi-barberia");
        assertThat(res.username()).isEqualTo("juan");
        verify(tenantRepo).save(argThat(t -> t.getId().equals("mi-barberia")));
        verify(configRepo).save(argThat(c -> c.getTenantId().equals("mi-barberia")));
        verify(usuarioRepo).save(argThat(u -> u.getRol().equals("ADMIN") && u.getTenantId().equals("mi-barberia")));
        verify(codigoRepo).save(argThat(c -> c.isUsado() && "juan".equals(c.getUsadoPorUsername())));
    }
}
