package com.agente.atencion.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DashboardCalculator {

    private DashboardCalculator() {}

    public record Resumen(long total, long reservados, long completados, long cancelados,
                          long ingresoEstimado, long ingresoReal, List<Map<String, Object>> barberos) {}

    // filas: [barberoId, estado, servicio, cantidad] ya agrupadas por la base
    public static Resumen calcular(List<Object[]> filas, Map<String, Double> precios, Map<Long, String> nombres) {
        long total = 0, reservados = 0, completados = 0, cancelados = 0;
        double estimado = 0, real = 0;
        Map<Long, double[]> porBarbero = new TreeMap<>();

        for (Object[] f : filas) {
            Long barberoId = (Long) f[0];
            String estado = (String) f[1];
            String servicio = (String) f[2];
            long n = ((Number) f[3]).longValue();
            boolean cancelado = "CANCELADO".equals(estado);
            boolean completado = "COMPLETADO".equals(estado);
            double precio = servicio == null ? 0.0 : precios.getOrDefault(servicio, 0.0);
            double monto = cancelado ? 0.0 : n * precio;

            total += n;
            if ("RESERVADO".equals(estado)) reservados += n;
            if (completado) completados += n;
            if (cancelado) cancelados += n;
            estimado += monto;
            if (completado) real += n * precio;

            if (barberoId != null) {
                double[] acc = porBarbero.computeIfAbsent(barberoId, k -> new double[3]);
                acc[0] += n;
                if (completado) acc[1] += n;
                acc[2] += monto;
            }
        }

        List<Map<String, Object>> barberos = new ArrayList<>();
        porBarbero.forEach((id, acc) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("barberoId", id);
            m.put("nombre", nombres.getOrDefault(id, "Barbero #" + id));
            m.put("total", (long) acc[0]);
            m.put("completados", (long) acc[1]);
            m.put("ingreso", (long) acc[2]);
            barberos.add(m);
        });
        return new Resumen(total, reservados, completados, cancelados, (long) estimado, (long) real, barberos);
    }

    public static List<Map<String, Object>> rankingPorTotal(List<Map<String, Object>> barberos) {
        return barberos.stream()
            .sorted(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("total")).reversed())
            .toList();
    }

    public static List<Map<String, Object>> sinIngreso(List<Map<String, Object>> barberos) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : barberos) {
            Map<String, Object> c = new LinkedHashMap<>(m);
            c.remove("ingreso");
            out.add(c);
        }
        return out;
    }
}
