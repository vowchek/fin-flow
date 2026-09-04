ALTER TABLE app_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE app_user ADD CONSTRAINT app_user_role CHECK (role IN ('USER', 'ADMIN'));

CREATE TABLE stock_instrument (
    id              UUID PRIMARY KEY,
    symbol          VARCHAR(32) NOT NULL,
    external_id     VARCHAR(120) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    logo_filename   VARCHAR(120),
    currency        VARCHAR(8) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_stock_instrument_symbol UNIQUE (symbol),
    CONSTRAINT uq_stock_instrument_external UNIQUE (external_id)
);

CREATE TABLE crypto_instrument (
    id              UUID PRIMARY KEY,
    symbol          VARCHAR(32) NOT NULL,
    external_id     VARCHAR(120) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    logo_filename   VARCHAR(120),
    currency        VARCHAR(8) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_crypto_instrument_symbol UNIQUE (symbol),
    CONSTRAINT uq_crypto_instrument_external UNIQUE (external_id)
);

INSERT INTO stock_instrument (id, symbol, external_id, name, logo_filename, currency, enabled, created_at, updated_at)
SELECT id, symbol, external_id, name, NULL, currency, TRUE, created_at, updated_at
FROM instrument
WHERE market = 'MOEX';

INSERT INTO crypto_instrument (id, symbol, external_id, name, logo_filename, currency, enabled, created_at, updated_at)
SELECT id, symbol, external_id, name, NULL, currency, TRUE, created_at, updated_at
FROM instrument
WHERE market = 'CRYPTO';

DROP TABLE price_history;
DROP TABLE price_quote;
DROP TABLE instrument;

CREATE TABLE price_quote (
    market          VARCHAR(16) NOT NULL,
    instrument_id   UUID NOT NULL,
    price           NUMERIC(28, 8) NOT NULL,
    previous_close  NUMERIC(28, 8),
    currency        VARCHAR(8) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT price_quote_pk PRIMARY KEY (market, instrument_id),
    CONSTRAINT price_quote_market CHECK (market IN ('MOEX', 'CRYPTO')),
    CONSTRAINT price_quote_price_positive CHECK (price > 0),
    CONSTRAINT price_quote_previous_positive CHECK (previous_close IS NULL OR previous_close > 0)
);

CREATE TABLE price_history (
    id              UUID PRIMARY KEY,
    market          VARCHAR(16) NOT NULL,
    instrument_id   UUID NOT NULL,
    price_date      DATE NOT NULL,
    price           NUMERIC(28, 8) NOT NULL,
    currency        VARCHAR(8) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT price_history_market CHECK (market IN ('MOEX', 'CRYPTO')),
    CONSTRAINT price_history_price_positive CHECK (price > 0),
    CONSTRAINT price_history_unique UNIQUE (market, instrument_id, price_date)
);

CREATE INDEX idx_price_history_lookup ON price_history (market, instrument_id, price_date);
CREATE INDEX idx_stock_instrument_enabled ON stock_instrument (enabled, symbol);
CREATE INDEX idx_crypto_instrument_enabled ON crypto_instrument (enabled, symbol);
