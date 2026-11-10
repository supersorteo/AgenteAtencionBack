CREATE TABLE IF NOT EXISTS pagos (
    id              BIGSERIAL      NOT NULL,
    referencia      VARCHAR(36)    NOT NULL,
    tenant_id       VARCHAR(255),
    proveedor       VARCHAR(20)    NOT NULL,
    external_id     VARCHAR(100),
    tipo_plan       VARCHAR(20)    NOT NULL,
    monto           NUMERIC(19,2)  NOT NULL,
    moneda          VARCHAR(3)     NOT NULL,
    estado          VARCHAR(20)    NOT NULL,
    codigo_generado VARCHAR(255),
    creado_at       TIMESTAMP      NOT NULL,
    confirmado_at   TIMESTAMP,
    payload         TEXT,
    CONSTRAINT pk_pagos PRIMARY KEY (id),
    CONSTRAINT uq_pagos_referencia UNIQUE (referencia),
    CONSTRAINT uq_pagos_proveedor_external UNIQUE (proveedor, external_id)
);

CREATE INDEX IF NOT EXISTS idx_pagos_tenant ON pagos (tenant_id);
