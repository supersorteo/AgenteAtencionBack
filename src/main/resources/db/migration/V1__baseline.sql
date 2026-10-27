-- =============================================================================
-- V1 — Baseline del esquema completo (13 tablas)
-- Corre solo en entornos frescos. En BD existentes Flyway hace baseline en V1
-- y solo ejecuta V2+ (ver spring.flyway.baseline-on-migrate en application.properties)
-- =============================================================================

-- Cleanup de tablas huérfanas de un proyecto anterior (inmobiliaria / clínica)
DROP TABLE IF EXISTS visitas;
DROP TABLE IF EXISTS propiedades;

-- -----------------------------------------------------------------------------
-- tenants
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
    id                              VARCHAR(255) NOT NULL,
    nombre                          VARCHAR(255),
    contexto                        TEXT,
    activo                          BOOLEAN      NOT NULL DEFAULT TRUE,
    tour_visto                      BOOLEAN               DEFAULT FALSE,
    fecha_vencimiento_suscripcion   DATE,
    fecha_registro                  DATE,
    CONSTRAINT pk_tenants PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- usuarios
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuarios (
    id          BIGSERIAL    NOT NULL,
    username    VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    tenant_id   VARCHAR(255) NOT NULL,
    rol         VARCHAR(255) NOT NULL,
    barbero_id  BIGINT,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_usuarios_username UNIQUE (username)
);

-- -----------------------------------------------------------------------------
-- barberos
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS barberos (
    id            BIGSERIAL    NOT NULL,
    tenant_id     VARCHAR(255),
    nombre        VARCHAR(255),
    especialidad  VARCHAR(255),
    foto          VARCHAR(255),
    activo        BOOLEAN DEFAULT TRUE,
    CONSTRAINT pk_barberos PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- horarios_barbero
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS horarios_barbero (
    id          BIGSERIAL NOT NULL,
    barbero_id  BIGINT,
    dia_semana  INTEGER,          -- 1=Lun … 7=Dom
    hora_inicio VARCHAR(255),
    hora_fin    VARCHAR(255),
    CONSTRAINT pk_horarios_barbero PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- bloqueos_horario
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bloqueos_horario (
    id          BIGSERIAL    NOT NULL,
    barbero_id  BIGINT,
    tenant_id   VARCHAR(255),
    fecha       DATE,
    hora_inicio VARCHAR(255),
    hora_fin    VARCHAR(255),
    motivo      VARCHAR(255),
    creado_en   TIMESTAMP,
    CONSTRAINT pk_bloqueos_horario PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- categoria_servicio
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categoria_servicio (
    id          BIGSERIAL    NOT NULL,
    tenant_id   VARCHAR(255),
    nombre      VARCHAR(255),
    emoji       VARCHAR(255),
    imagen_url  VARCHAR(255),
    orden       INTEGER      NOT NULL DEFAULT 0,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_categoria_servicio PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- servicios
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS servicios (
    id                BIGSERIAL    NOT NULL,
    tenant_id         VARCHAR(255),
    nombre            VARCHAR(255),
    descripcion       TEXT,
    precio            FLOAT8,
    duracion_minutos  INTEGER,
    emoji             VARCHAR(255) DEFAULT '✂️',
    categoria         VARCHAR(255),
    imagen_url        VARCHAR(255),
    imagen_url2       VARCHAR(255),
    imagen_url3       VARCHAR(255),
    activo            BOOLEAN DEFAULT TRUE,
    CONSTRAINT pk_servicios PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- negocio_config
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS negocio_config (
    tenant_id        VARCHAR(255) NOT NULL,
    nombre           VARCHAR(255),
    tagline          VARCHAR(255),
    hero_titulo1     VARCHAR(255),
    hero_titulo2     VARCHAR(255),
    hero_desc        TEXT,
    footer_desc      TEXT,
    direccion        VARCHAR(255),
    telefono         VARCHAR(255),
    email            VARCHAR(255),
    whatsapp         VARCHAR(255),
    instagram_handle VARCHAR(255),
    time_zone        VARCHAR(255),
    color_primario   VARCHAR(255),
    color_fondo      VARCHAR(255),
    color_texto      VARCHAR(255),
    logo_url         VARCHAR(255),
    hero_bg_url      VARCHAR(255),
    moneda           VARCHAR(255),
    pais_codigo      VARCHAR(255),
    horario1         VARCHAR(255),
    horario2         VARCHAR(255),
    horario3         VARCHAR(255),
    t1_nombre        VARCHAR(255),
    t1_iniciales     VARCHAR(255),
    t1_servicio      VARCHAR(255),
    t1_texto         TEXT,
    t2_nombre        VARCHAR(255),
    t2_iniciales     VARCHAR(255),
    t2_servicio      VARCHAR(255),
    t2_texto         TEXT,
    t3_nombre        VARCHAR(255),
    t3_iniciales     VARCHAR(255),
    t3_servicio      VARCHAR(255),
    t3_texto         TEXT,
    CONSTRAINT pk_negocio_config PRIMARY KEY (tenant_id)
);

-- -----------------------------------------------------------------------------
-- turnos (3 índices compuestos declarados en la entidad @Index)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS turnos (
    id          BIGSERIAL    NOT NULL,
    tenant_id   VARCHAR(255),
    paciente    VARCHAR(255),
    telefono    VARCHAR(255),
    servicio    VARCHAR(255),
    barbero_id  BIGINT,
    fecha       DATE,
    hora        VARCHAR(255),
    hora_fin    VARCHAR(255),
    estado      VARCHAR(255) DEFAULT 'RESERVADO',
    creado_en   TIMESTAMP,
    CONSTRAINT pk_turnos PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_turnos_tenant_fecha   ON turnos (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_turnos_tenant_barbero ON turnos (tenant_id, barbero_id);
CREATE INDEX IF NOT EXISTS idx_turnos_tenant_estado  ON turnos (tenant_id, estado);

-- -----------------------------------------------------------------------------
-- galeria
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS galeria (
    id          BIGSERIAL    NOT NULL,
    tenant_id   VARCHAR(255),
    titulo      VARCHAR(255),
    descripcion TEXT,
    imagen_url  VARCHAR(255),
    categoria   VARCHAR(255),
    servicio_id BIGINT,
    activo      BOOLEAN DEFAULT TRUE,
    CONSTRAINT pk_galeria PRIMARY KEY (id)
);

-- -----------------------------------------------------------------------------
-- codigos_invitacion
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS codigos_invitacion (
    codigo               VARCHAR(255) NOT NULL,
    tipo                 VARCHAR(50),              -- TipoCodigo enum: PLAN_1M | PLAN_3M | PLAN_6M | PLAN_12M
    usado                BOOLEAN      NOT NULL DEFAULT FALSE,
    usado_por_username   VARCHAR(255),
    usado_at             TIMESTAMP,
    fecha_vencimiento    DATE,
    creado_at            TIMESTAMP,
    CONSTRAINT pk_codigos_invitacion PRIMARY KEY (codigo)
);

-- -----------------------------------------------------------------------------
-- plan_precios
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plan_precios (
    tipo        VARCHAR(50)    NOT NULL,            -- TipoCodigo enum como PK
    precio_usd  NUMERIC(19,2),
    precio_uyu  NUMERIC(19,2),
    precio_ars  NUMERIC(19,2),
    CONSTRAINT pk_plan_precios PRIMARY KEY (tipo)
);

-- -----------------------------------------------------------------------------
-- system_config
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
    clave VARCHAR(255) NOT NULL,
    valor TEXT         NOT NULL,
    CONSTRAINT pk_system_config PRIMARY KEY (clave)
);
