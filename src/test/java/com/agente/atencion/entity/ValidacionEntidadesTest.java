package com.agente.atencion.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidacionEntidadesTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private static String largo(int n) {
        return "x".repeat(n);
    }

    private <T> Set<String> campos(T objeto) {
        return validador.validate(objeto).stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void unaActualizacionParcialConTodoNuloEsValida() {
        assertTrue(validador.validate(new Barbero()).isEmpty());
        assertTrue(validador.validate(new Servicio()).isEmpty());
        assertTrue(validador.validate(new Galeria()).isEmpty());
        assertTrue(validador.validate(new NegocioConfig()).isEmpty());
        assertTrue(validador.validate(new HorarioBarbero()).isEmpty());
        assertTrue(validador.validate(new CategoriaServicio()).isEmpty());
        assertTrue(validador.validate(new BloqueoHorario()).isEmpty());
    }

    @Test
    void servicioRechazaTextosLargosPreciosNegativosYDuracionesAbsurdas() {
        Servicio s = new Servicio();
        s.setNombre(largo(101));
        s.setDescripcion(largo(5001));
        s.setPrecio(-5.0);
        s.setDuracionMinutos(0);
        s.setImagenUrl(largo(256));

        assertEquals(Set.of("nombre", "descripcion", "precio", "duracionMinutos", "imagenUrl"), campos(s));
    }

    @Test
    void servicioNormalEsValido() {
        Servicio s = new Servicio();
        s.setNombre("Corte clásico");
        s.setDescripcion("Corte a tijera");
        s.setPrecio(450.0);
        s.setDuracionMinutos(30);
        s.setImagenUrl("https://res.cloudinary.com/x/image/upload/v1/a.jpg");

        assertTrue(validador.validate(s).isEmpty());
    }

    @Test
    void horarioExigeDiaDelUnoAlSieteYHoraConFormato() {
        HorarioBarbero h = new HorarioBarbero();
        h.setDiaSemana(0);
        h.setHoraInicio("25:00");
        h.setHoraFin("9h");
        assertEquals(Set.of("diaSemana", "horaInicio", "horaFin"), campos(h));

        h.setDiaSemana(7);
        h.setHoraInicio("09:00");
        h.setHoraFin("18:30");
        assertTrue(validador.validate(h).isEmpty());
    }

    @Test
    void bloqueoValidaElFormatoDeHora() {
        BloqueoHorario b = new BloqueoHorario();
        b.setHoraInicio("abc");
        b.setMotivo(largo(256));

        assertEquals(Set.of("horaInicio", "motivo"), campos(b));
    }

    @Test
    void configAcotaLosCamposQueAlimentanLaLandingYElAgente() {
        NegocioConfig c = new NegocioConfig();
        c.setNombre(largo(256));
        c.setHeroDesc(largo(5001));
        c.setEmail(largo(121));

        assertEquals(Set.of("nombre", "heroDesc", "email"), campos(c));
    }
}
