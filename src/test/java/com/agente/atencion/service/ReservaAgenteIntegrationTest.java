package com.agente.atencion.service;

import com.agente.atencion.controller.TurnoController;
import com.agente.atencion.entity.*;
import com.agente.atencion.repository.*;
import com.agente.atencion.security.UsuarioAutenticado;
import com.agente.atencion.tools.NegocioTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(ReservaAgenteIntegrationTest.Config.class)
class ReservaAgenteIntegrationTest {
    private static final String TENANT = "barberia-demo";
    private static final LocalDate FECHA = LocalDate.of(2026, 9, 22);
    @Autowired ReservaAgenteService reservas;
    @Autowired NegocioTools tools;
    @Autowired DisponibilidadService disponibilidad;
    @Autowired TurnoController controller;
    @Autowired TurnoRepository turnos;
    @Autowired BarberoRepository barberos;
    @Autowired ServicioRepository servicios;
    @Autowired HorarioBarberoRepository horarios;
    @Autowired BloqueoHorarioRepository bloqueos;
    private Barbero juan;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = TurnoRepository.class)
    @Import({ReservaAgenteService.class, DisponibilidadService.class, NegocioTools.class, TurnoController.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:reservas-agente;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.agente.atencion.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean Clock negocioClock() {
            // UTC is already Tuesday; Montevideo is still Monday. Relative dates must use the business zone.
            return Clock.fixed(Instant.parse("2026-09-22T01:00:00Z"), ZoneId.of("America/Montevideo"));
        }
    }

    @BeforeEach
    void prepararAgenda() {
        turnos.deleteAll();
        bloqueos.deleteAll();
        horarios.deleteAll();
        barberos.deleteAll();
        servicios.deleteAll();
        juan = new Barbero();
        juan.setTenantId(TENANT);
        juan.setNombre("Juan García");
        juan = barberos.saveAndFlush(juan);
        var horario = new HorarioBarbero();
        horario.setBarberoId(juan.getId());
        horario.setDiaSemana(2);
        horario.setHoraInicio("09:00");
        horario.setHoraFin("12:00");
        horarios.saveAndFlush(horario);
        var servicio = new Servicio();
        servicio.setTenantId(TENANT);
        servicio.setNombre("Corte clásico");
        servicio.setPrecio(400.0);
        servicio.setDuracionMinutos(45);
        servicios.saveAndFlush(servicio);
    }

    @Test
    void herramientaGuardaLosSeisDatosYLosPanelesLeenLaMismaReserva() {
        String resultado = reservarDesdeHerramienta(datos());
        assertThat(resultado).startsWith("Turno confirmado #");
        var guardado = turnos.findAll().getFirst();
        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getPaciente()).isEqualTo("Ana Pérez");
        assertThat(guardado.getTelefono()).isEqualTo("+59899123456");
        assertThat(guardado.getServicio()).isEqualTo("Corte clásico");
        assertThat(guardado.getBarberoId()).isEqualTo(juan.getId());
        assertThat(guardado.getTenantId()).isEqualTo(TENANT);
        assertThat(guardado.getFecha()).isEqualTo(FECHA);
        assertThat(guardado.getHora()).isEqualTo("10:00");
        assertThat(guardado.getHoraFin()).isEqualTo("10:45");
        assertThat(guardado.getEstado()).isEqualTo("RESERVADO");
        var admin = controller.listar(TENANT, FECHA.toString(), auth("ADMIN", TENANT, null));
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).extracting(Turno::getId).containsExactly(guardado.getId());
        var agenda = controller.misTurnos(TENANT, FECHA.toString(), auth("BARBERO", TENANT, juan.getId()));
        assertThat(agenda.getBody()).extracting(Turno::getId).containsExactly(guardado.getId());
        var otroBarbero = controller.misTurnos(TENANT, FECHA.toString(), auth("BARBERO", TENANT, juan.getId() + 1));
        assertThat(otroBarbero.getBody()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void nuncaGuardaSiFaltaCualquieraDeLosSeisDatos(int campo) {
        var valores = datos();
        valores[campo] = " ";
        assertThat(reservarDesdeHerramienta(valores)).contains("No se creó la reserva", "Faltan datos obligatorios");
        assertThat(turnos.count()).isZero();
        valores[campo] = null;
        assertThat(reservarDesdeHerramienta(valores)).contains("Faltan datos obligatorios");
        assertThat(turnos.count()).isZero();
    }

    @Test
    void rechazaTelefonoFechaHoraYCatalogoInvalidosSinGuardar() {
        for (String[] cambio : List.of(
                new String[]{"1", "123"}, new String[]{"1", "no tengo"},
                new String[]{"2", "Servicio inventado"}, new String[]{"3", "Barbero inventado"},
                new String[]{"4", "2026-02-30"}, new String[]{"4", "2026-09-20"},
                new String[]{"5", "25:00"}, new String[]{"5", "9am"}, new String[]{"0", "123"})) {
            var valores = datos();
            valores[Integer.parseInt(cambio[0])] = cambio[1];
            assertThat(reservarDesdeHerramienta(valores)).startsWith("No se creó la reserva");
        }
        assertThat(turnos.count()).isZero();
    }

    @Test
    void noReservaFueraDeJornadaNiEnDiaLibreNiCuandoLaDuracionExcedeElCierre() {
        for (String hora : List.of("08:30", "11:30", "12:00", "10:15")) {
            var valores = datos(); valores[5] = hora;
            assertThat(reservarDesdeHerramienta(valores)).contains("No se creó la reserva");
        }
        var valores = datos(); valores[4] = "2026-09-23";
        assertThat(reservarDesdeHerramienta(valores)).contains("No se creó la reserva");
        assertThat(turnos.count()).isZero();
    }

    @Test
    void respetaBloqueosSinBloquearElIntervaloAdyacente() {
        var bloqueo = new BloqueoHorario();
        bloqueo.setTenantId(TENANT); bloqueo.setBarberoId(juan.getId()); bloqueo.setFecha(FECHA);
        bloqueo.setHoraInicio("10:45"); bloqueo.setHoraFin("11:30");
        bloqueos.saveAndFlush(bloqueo);
        var slots = disponibilidad.calcular(TENANT, FECHA, "Corte clásico", juan.getId());
        assertThat(slots).anyMatch(s -> s.hora().equals("10:00"));
        assertThat(slots).noneMatch(s -> s.hora().equals("10:30") || s.hora().equals("11:00"));
        assertThat(disponibilidad.validar(juan.getId(), FECHA, "10:00", "10:45")).isTrue();
        var valores = datos(); valores[5] = "10:30";
        assertThat(reservarDesdeHerramienta(valores)).contains("No se creó la reserva");
        assertThat(reservarDesdeHerramienta(datos())).startsWith("Turno confirmado #");
    }

    @Test
    void reservaCanceladaLiberaElHorarioYUnaRepeticionNoDuplica() {
        assertThat(reservarDesdeHerramienta(datos())).startsWith("Turno confirmado #");
        assertThat(reservarDesdeHerramienta(datos())).contains("No se creó la reserva");
        assertThat(turnos.count()).isEqualTo(1);
        var turno = turnos.findAll().getFirst(); turno.setEstado("CANCELADO"); turnos.saveAndFlush(turno);
        assertThat(reservarDesdeHerramienta(datos())).startsWith("Turno confirmado #");
        assertThat(turnos.count()).isEqualTo(2);
    }

    @Test
    void agenteYFormularioSimultaneosNoPuedenReservarElMismoHorario() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var agente = executor.submit(() -> { inicio.await(); return reservarDesdeHerramienta(datos()).startsWith("Turno confirmado #"); });
            var formulario = executor.submit(() -> {
                inicio.await();
                var turno = new Turno();
                turno.setPaciente("Otro cliente"); turno.setTelefono("099999999");
                turno.setServicio("Corte clásico"); turno.setBarberoId(juan.getId());
                turno.setFecha(FECHA); turno.setHora("10:00");
                return controller.crear(TENANT, turno).getStatusCode().is2xxSuccessful();
            });
            inicio.countDown();
            assertThat(List.of(agente.get(15, TimeUnit.SECONDS), formulario.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
            assertThat(turnos.count()).isEqualTo(1);
        }
    }

    @Test
    void consultasDeAgendaSonPrivadasYRestringidasAlNegocio() {
        assertThat(controller.listar(TENANT, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.listar(TENANT, null, auth("BARBERO", TENANT, juan.getId())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.listar(TENANT, null, auth("ADMIN", "otro-negocio", null)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.misTurnos(TENANT, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.misTurnos(TENANT, null, auth("BARBERO", "otro-negocio", juan.getId())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void barberoInactivoODeOtroNegocioNoSePuedeSeleccionar() {
        juan.setActivo(false); barberos.saveAndFlush(juan);
        assertThat(reservarDesdeHerramienta(datos())).contains("No se creó la reserva");
        assertThat(disponibilidad.calcular(TENANT, FECHA, "Corte clásico", juan.getId())).isEmpty();
        juan.setActivo(true); juan.setTenantId("otro-negocio"); barberos.saveAndFlush(juan);
        assertThat(reservarDesdeHerramienta(datos())).contains("No se creó la reserva");
        assertThat(disponibilidad.calcular(TENANT, FECHA, "Corte clásico", juan.getId())).isEmpty();
    }

    @Test
    void noOfreceNiReservaHorasPasadasDelDiaActualEnMontevideo() {
        var horario = new HorarioBarbero();
        horario.setBarberoId(juan.getId()); horario.setDiaSemana(1);
        horario.setHoraInicio("21:00"); horario.setHoraFin("23:59");
        horarios.saveAndFlush(horario);
        var hoy = LocalDate.of(2026, 9, 21);
        var slots = disponibilidad.calcular(TENANT, hoy, "Corte clásico", juan.getId());
        assertThat(slots).extracting(DisponibilidadService.SlotDisponible::hora).containsExactly("22:30", "23:00");
        var valores = datos(); valores[4] = hoy.toString(); valores[5] = "21:30";
        assertThat(reservarDesdeHerramienta(valores)).contains("Ese horario ya pasó");
        assertThat(turnos.count()).isZero();
    }

    @Test
    void consultaDeDisponibilidadNoSustituyeBarberoDesconocidoYUsaFechaLocal() {
        TenantContext.set(TENANT);
        try {
            assertThat(tools.consultarDisponibilidad(FECHA.toString(), "Corte clásico", "Desconocido")).contains("No se asignó otro barbero");
            assertThat(tools.consultarDisponibilidad("fecha inválida", "Corte clásico", null)).contains("fecha válida");
            assertThat(tools.consultarDisponibilidad(FECHA.toString(), "Corte clásico", null)).contains("Juan García", "10:00");
            assertThat(tools.obtenerFechaActual()).contains("HOY es lunes 2026-09-21", "mañana = 2026-09-22");
        } finally { TenantContext.clear(); }
    }

    private String[] datos() {
        return new String[]{"Ana Pérez", "+598 99 123 456", "corte clasico", "juan garcia", FECHA.toString(), "10:00"};
    }

    private String reservarDesdeHerramienta(String[] valores) {
        TenantContext.set(TENANT);
        try { return tools.reservarTurno(valores[0], valores[2], valores[4], valores[5], valores[1], valores[3]); }
        finally { TenantContext.clear(); }
    }

    private UsernamePasswordAuthenticationToken auth(String rol, String tenant, Long barberoId) {
        return new UsernamePasswordAuthenticationToken(new UsuarioAutenticado("test", rol, tenant, barberoId), null, List.of());
    }
}
