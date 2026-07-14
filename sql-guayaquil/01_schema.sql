-- Esquema del nodo Guayaquil (Ejercicio propuesto 1)
-- Fragmento horizontal: filas con oficina = 'GUAYAQUIL'
-- Convencion de prefijo: las cuentas de Guayaquil empiezan con 09
-- (primeros digitos de la cedula ecuatoriana asociada a la provincia del Guayas)

CREATE TABLE IF NOT EXISTS clientes (
    id          BIGSERIAL PRIMARY KEY,
    cedula      VARCHAR(10) UNIQUE NOT NULL,
    nombre      VARCHAR(120) NOT NULL,
    ciudad      VARCHAR(60)  NOT NULL,
    creado_en   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cuentas (
    id          BIGSERIAL PRIMARY KEY,
    numero      VARCHAR(20) UNIQUE NOT NULL,
    cliente_id  BIGINT      NOT NULL REFERENCES clientes(id),
    saldo       NUMERIC(15,2) NOT NULL DEFAULT 0.00
                CHECK (saldo >= 0),
    oficina     VARCHAR(20) NOT NULL CHECK (oficina = 'GUAYAQUIL'),
    abierta_en  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transacciones (
    id           BIGSERIAL PRIMARY KEY,
    cuenta_orig  VARCHAR(20) NOT NULL,
    cuenta_dest  VARCHAR(20) NOT NULL,
    monto        NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    fecha        TIMESTAMP   NOT NULL DEFAULT NOW(),
    estado       VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA',
    oficina      VARCHAR(20) NOT NULL CHECK (oficina = 'GUAYAQUIL')
);

CREATE INDEX idx_cuentas_cliente       ON cuentas(cliente_id);
CREATE INDEX idx_transacciones_orig    ON transacciones(cuenta_orig);
CREATE INDEX idx_transacciones_fecha   ON transacciones(fecha);
