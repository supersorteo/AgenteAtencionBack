-- =============================================================================
-- V3 — Columnas y tabla agregadas en el sprint superadmin (oct 2026)
-- Estas nunca llegaron a producción via ddl-auto=update antes del switch a Flyway
-- =============================================================================

-- codigos_invitacion: tipo de plan y fecha de vencimiento del código
ALTER TABLE codigos_invitacion ADD COLUMN IF NOT EXISTS tipo            VARCHAR(50);
ALTER TABLE codigos_invitacion ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;

-- tenants: fecha en que se registró el tenant
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS fecha_registro DATE;

-- plan_precios: tabla nueva (precios por plan en 3 monedas)
CREATE TABLE IF NOT EXISTS plan_precios (
    tipo        VARCHAR(50)    NOT NULL,
    precio_usd  NUMERIC(19,2),
    precio_uyu  NUMERIC(19,2),
    precio_ars  NUMERIC(19,2),
    CONSTRAINT pk_plan_precios PRIMARY KEY (tipo)
);
