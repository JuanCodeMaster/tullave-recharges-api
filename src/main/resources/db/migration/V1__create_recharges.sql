-- Tabla de recargas digitales de tarjetas tuLlave.
-- Las reglas de negocio se validan en la API (Bean Validation) y además se refuerzan en la base de datos
-- para que ningún otro cliente (scripts, otras apps) pueda dejar datos inconsistentes.

CREATE TABLE recharges (
    id              BIGSERIAL PRIMARY KEY,
    reference       UUID            NOT NULL,
    card_number     VARCHAR(16)     NOT NULL,
    amount          NUMERIC(12, 2)  NOT NULL,
    payment_method  VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_recharges_reference      UNIQUE (reference),
    CONSTRAINT ck_recharges_card_number    CHECK (card_number ~ '^[0-9]{16}$'),
    CONSTRAINT ck_recharges_amount         CHECK (amount >= 2000 AND amount <= 200000),
    CONSTRAINT ck_recharges_payment_method CHECK (payment_method IN ('PSE', 'NEQUI', 'DAVIPLATA', 'CREDIT_CARD'))
);

-- Soporta el filtro por tarjeta del listado y el orden por fecha de creación.
CREATE INDEX idx_recharges_card_number_created_at ON recharges (card_number, created_at DESC);
CREATE INDEX idx_recharges_created_at ON recharges (created_at DESC);

COMMENT ON TABLE recharges IS 'Recargas digitales registradas sobre tarjetas tuLlave';
COMMENT ON COLUMN recharges.reference IS 'Identificador público de la transacción (comprobante)';
COMMENT ON COLUMN recharges.card_number IS 'Número de tarjeta tuLlave, 16 dígitos';
COMMENT ON COLUMN recharges.amount IS 'Monto recargado en COP';
