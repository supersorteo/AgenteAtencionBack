# Convención de migraciones con Flyway

## Herramienta
Flyway — archivos SQL en `src/main/resources/db/migration/`

## Naming
```
V{N}__{descripcion_snake_case}.sql
```
Ejemplos:
```
V3__add_notas_to_turnos.sql
V4__add_public_id_to_galeria.sql
V5__create_notificaciones.sql
```

## Historial actual

| Versión | Archivo | Descripción |
|---------|---------|-------------|
| V1 | `V1__baseline.sql` | Esquema completo (13 tablas). Corre solo en entornos frescos; en producción Flyway hace baseline aquí. |
| V2 | `V2__seed_barberia_demo.sql` | Datos de demostración (tenant `barberia-demo`, 12 servicios, 4 categorías, 4 galería). |

## Reglas

1. **Inmutabilidad**: los archivos aplicados no se modifican nunca. Si hubo un error, crear `V(N+1)` que lo corrija.
2. **Idempotencia DDL**: usar `IF NOT EXISTS` / `IF EXISTS` en todo DDL.
3. **Idempotencia DML**: los INSERT de seed usan `ON CONFLICT DO NOTHING`.
4. **Nunca usar `ddl-auto=update`** para probar un cambio — crear la migración correspondiente.
5. **Tests (H2)**: Flyway está desactivado en `application-test.properties`; Hibernate gestiona el esquema H2 con `create-drop`.

## Ejemplos de migraciones frecuentes

**Agregar una columna:**
```sql
ALTER TABLE turnos ADD COLUMN IF NOT EXISTS notas TEXT;
```

**Crear una tabla nueva:**
```sql
CREATE TABLE IF NOT EXISTS notificaciones (
    id        BIGSERIAL    NOT NULL,
    tenant_id VARCHAR(255),
    mensaje   TEXT,
    leida     BOOLEAN DEFAULT FALSE,
    CONSTRAINT pk_notificaciones PRIMARY KEY (id)
);
```

**Renombrar una columna (cuidado: no hay IF EXISTS en RENAME):**
```sql
ALTER TABLE barberos RENAME COLUMN especialidad TO bio;
```

**Agregar un índice:**
```sql
CREATE INDEX IF NOT EXISTS idx_turnos_telefono ON turnos (telefono);
```

## Comportamiento en cada entorno

| Entorno | `ddl-auto` | Flyway | Qué pasa al arrancar |
|---------|-----------|--------|----------------------|
| Local / Staging (fresco) | `validate` | habilitado | V1 crea esquema → V2 seed → Hibernate valida → OK |
| Producción (Railway, BD existente) | `validate` | habilitado con baseline | Flyway hace baseline en V1 → ejecuta solo V2+ → Hibernate valida → OK |
| Tests | `create-drop` | deshabilitado | Hibernate crea esquema desde entidades → tests corren |
