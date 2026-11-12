CREATE TABLE IF NOT EXISTS demo_credenciales (
    tenant_id VARCHAR(255) PRIMARY KEY,
    username  VARCHAR(255) NOT NULL,
    password  VARCHAR(255) NOT NULL
);

-- Cuenta pública de la barbería demo: el login la muestra precargada. El superadmin la actualiza al reiniciar la contraseña de la demo
INSERT INTO demo_credenciales (tenant_id, username, password)
VALUES ('barberia-demo', 'admin', 'admin123')
ON CONFLICT (tenant_id) DO NOTHING;
