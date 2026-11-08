-- =============================================================================
-- V4 — Índices faltantes en tablas del flujo de disponibilidad
-- Estas columnas se consultan N veces por barbero en cada llamada al agente IA
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_horarios_barbero_id
    ON horarios_barbero (barbero_id);

CREATE INDEX IF NOT EXISTS idx_bloqueos_barbero_fecha
    ON bloqueos_horario (barbero_id, fecha);

CREATE INDEX IF NOT EXISTS idx_turnos_barbero_fecha
    ON turnos (barbero_id, fecha);
