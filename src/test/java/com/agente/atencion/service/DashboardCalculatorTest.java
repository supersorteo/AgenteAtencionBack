package com.agente.atencion.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DashboardCalculatorTest {

    private static final Map<String, Double> PRECIOS = Map.of("Corte", 300.0, "Barba", 150.5);
    private static final Map<Long, String> NOMBRES = Map.of(1L, "Juan", 2L, "Luis");

    private static Object[] fila(Long barbero, String estado, String servicio, long n) {
        return new Object[] {barbero, estado, servicio, n};
    }

    private final List<Object[]> filas = List.of(
        fila(1L, "COMPLETADO", "Corte", 3),
        fila(1L, "RESERVADO", "Barba", 2),
        fila(1L, "CANCELADO", "Corte", 4),
        fila(2L, "COMPLETADO", "Barba", 1),
        fila(2L, "RESERVADO", null, 1),
        fila(null, "RESERVADO", "Corte", 1),
        fila(3L, "COMPLETADO", "Servicio borrado", 2));

    @Test
    void cuentaPorEstadoIncluyendoTurnosSinBarbero() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(filas, PRECIOS, NOMBRES);

        assertEquals(14, r.total());
        assertEquals(4, r.reservados());
        assertEquals(6, r.completados());
        assertEquals(4, r.cancelados());
    }

    @Test
    void losCanceladosNoSumanIngresoYLosServiciosSinPrecioValenCero() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(filas, PRECIOS, NOMBRES);

        // estimado: 3*300 + 2*150.5 + 1*150.5 + 1*300 = 1651.5 -> 1651
        assertEquals(1651, r.ingresoEstimado());
        // real: 3*300 + 1*150.5 = 1050.5 -> 1050
        assertEquals(1050, r.ingresoReal());
    }

    @Test
    void desglosaPorBarberoOrdenadoPorIdYConNombreDeRespaldo() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(filas, PRECIOS, NOMBRES);

        assertEquals(3, r.barberos().size());
        Map<String, Object> juan = r.barberos().get(0);
        assertEquals(1L, juan.get("barberoId"));
        assertEquals("Juan", juan.get("nombre"));
        assertEquals(9L, juan.get("total"));
        assertEquals(3L, juan.get("completados"));
        assertEquals(1201L, juan.get("ingreso"));
        assertEquals("Barbero #3", r.barberos().get(2).get("nombre"));
    }

    @Test
    void elRankingVaDeMayorAMenorTotal() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(filas, PRECIOS, NOMBRES);

        List<Map<String, Object>> ranking = DashboardCalculator.rankingPorTotal(r.barberos());

        assertEquals(9L, ranking.get(0).get("total"));
        assertEquals(List.of(1L, 2L, 3L), ranking.stream().map(m -> m.get("barberoId")).toList());
    }

    @Test
    void elDesgloseDeHoyNoExponeIngresoPorBarbero() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(filas, PRECIOS, NOMBRES);

        assertFalse(DashboardCalculator.sinIngreso(r.barberos()).get(0).containsKey("ingreso"));
        assertEquals(true, r.barberos().get(0).containsKey("ingreso"));
    }

    @Test
    void sinTurnosTodoEnCero() {
        DashboardCalculator.Resumen r = DashboardCalculator.calcular(List.of(), PRECIOS, NOMBRES);

        assertEquals(0, r.total());
        assertEquals(0, r.ingresoEstimado());
        assertEquals(0, r.barberos().size());
    }
}
