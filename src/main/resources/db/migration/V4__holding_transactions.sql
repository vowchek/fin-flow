ALTER TABLE stock_holding DROP CONSTRAINT stock_holding_quantity_positive;
ALTER TABLE stock_holding ADD CONSTRAINT stock_holding_quantity_non_negative CHECK (quantity >= 0);

ALTER TABLE crypto_holding DROP CONSTRAINT crypto_holding_quantity_positive;
ALTER TABLE crypto_holding ADD CONSTRAINT crypto_holding_quantity_non_negative CHECK (quantity >= 0);

CREATE TABLE stock_transaction (
    id              UUID PRIMARY KEY,
    holding_id      UUID NOT NULL REFERENCES stock_holding (id) ON DELETE CASCADE,
    side            VARCHAR(8) NOT NULL,
    quantity        NUMERIC(28, 8) NOT NULL,
    occurred_on     DATE NOT NULL,
    unit_price      NUMERIC(28, 8),
    note            VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stock_transaction_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT stock_transaction_quantity_positive CHECK (quantity > 0),
    CONSTRAINT stock_transaction_unit_price_positive CHECK (unit_price IS NULL OR unit_price > 0)
);

CREATE INDEX idx_stock_transaction_holding_id ON stock_transaction (holding_id);
CREATE INDEX idx_stock_transaction_occurred_on ON stock_transaction (holding_id, occurred_on);

CREATE TABLE crypto_transaction (
    id              UUID PRIMARY KEY,
    holding_id      UUID NOT NULL REFERENCES crypto_holding (id) ON DELETE CASCADE,
    side            VARCHAR(8) NOT NULL,
    quantity        NUMERIC(28, 8) NOT NULL,
    occurred_on     DATE NOT NULL,
    unit_price      NUMERIC(28, 8),
    note            VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT crypto_transaction_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT crypto_transaction_quantity_positive CHECK (quantity > 0),
    CONSTRAINT crypto_transaction_unit_price_positive CHECK (unit_price IS NULL OR unit_price > 0)
);

CREATE INDEX idx_crypto_transaction_holding_id ON crypto_transaction (holding_id);
CREATE INDEX idx_crypto_transaction_occurred_on ON crypto_transaction (holding_id, occurred_on);

INSERT INTO stock_transaction (id, holding_id, side, quantity, occurred_on, unit_price, note, created_at)
SELECT gen_random_uuid(),
       id,
       'BUY',
       quantity,
       CAST(created_at AS DATE),
       NULL,
       NULL,
       created_at
FROM stock_holding;

INSERT INTO crypto_transaction (id, holding_id, side, quantity, occurred_on, unit_price, note, created_at)
SELECT gen_random_uuid(),
       id,
       'BUY',
       quantity,
       CAST(created_at AS DATE),
       NULL,
       NULL,
       created_at
FROM crypto_holding;
