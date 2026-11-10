-- Indices por tenant_id: consultas del panel admin y del sitio publico de cada barberia
CREATE INDEX IF NOT EXISTS idx_barberos_tenant            ON barberos (tenant_id);
CREATE INDEX IF NOT EXISTS idx_servicios_tenant           ON servicios (tenant_id);
CREATE INDEX IF NOT EXISTS idx_galeria_tenant             ON galeria (tenant_id);
CREATE INDEX IF NOT EXISTS idx_categoria_servicio_tenant  ON categoria_servicio (tenant_id);
CREATE INDEX IF NOT EXISTS idx_bloqueos_tenant            ON bloqueos_horario (tenant_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_tenant            ON usuarios (tenant_id);
CREATE INDEX IF NOT EXISTS idx_usuarios_barbero           ON usuarios (barbero_id);
CREATE INDEX IF NOT EXISTS idx_pagos_estado_creado        ON pagos (estado, creado_at);

-- Restricciones unicas. Si ya existen filas duplicadas el indice NO se crea (la migracion no debe
-- tumbar el despliegue): se emite un aviso y la aplicacion sigue protegida por el bloqueo de fila.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM turnos
        WHERE barbero_id IS NOT NULL AND hora IS NOT NULL AND COALESCE(estado, '') <> 'CANCELADO'
        GROUP BY barbero_id, fecha, hora
        HAVING COUNT(*) > 1
    ) THEN
        RAISE WARNING 'V8: hay turnos duplicados por barbero, fecha y hora; no se crea uq_turnos_barbero_fecha_hora_activo';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_turnos_barbero_fecha_hora_activo
            ON turnos (barbero_id, fecha, hora)
            WHERE barbero_id IS NOT NULL AND hora IS NOT NULL AND COALESCE(estado, '') <> 'CANCELADO';
    END IF;

    IF EXISTS (
        SELECT 1 FROM horarios_barbero
        GROUP BY barbero_id, dia_semana
        HAVING COUNT(*) > 1
    ) THEN
        RAISE WARNING 'V8: hay horarios duplicados por barbero y dia; no se crea uq_horarios_barbero_dia';
    ELSE
        CREATE UNIQUE INDEX IF NOT EXISTS uq_horarios_barbero_dia
            ON horarios_barbero (barbero_id, dia_semana);
    END IF;
END $$;
