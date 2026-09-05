-- Cached MOEX dividend/coupon history for catalog instruments.

CREATE TABLE stock_instrument_payment (
    id                  UUID PRIMARY KEY,
    instrument_id       UUID         NOT NULL REFERENCES stock_instrument (id) ON DELETE CASCADE,
    occurred_on         DATE         NOT NULL,
    amount_per_unit     NUMERIC(28, 8) NOT NULL,
    currency            VARCHAR(8)   NOT NULL,
    kind                VARCHAR(16)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT stock_instrument_payment_kind CHECK (kind IN ('DIVIDEND', 'COUPON')),
    CONSTRAINT stock_instrument_payment_amount CHECK (amount_per_unit > 0)
);

CREATE INDEX idx_stock_instrument_payment_instrument
    ON stock_instrument_payment (instrument_id, occurred_on DESC);

CREATE UNIQUE INDEX uq_stock_instrument_payment_row
    ON stock_instrument_payment (instrument_id, occurred_on, kind, amount_per_unit);
