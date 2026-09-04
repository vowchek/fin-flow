CREATE TABLE instrument (
    id              UUID PRIMARY KEY,
    market          VARCHAR(16) NOT NULL,
    symbol          VARCHAR(32) NOT NULL,
    external_id     VARCHAR(120) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    logo_url        VARCHAR(500),
    currency        VARCHAR(8) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT instrument_market CHECK (market IN ('MOEX', 'CRYPTO')),
    CONSTRAINT instrument_market_symbol UNIQUE (market, symbol),
    CONSTRAINT instrument_market_external UNIQUE (market, external_id)
);

CREATE INDEX idx_instrument_search ON instrument (market, symbol);
CREATE INDEX idx_instrument_name ON instrument (market, name);

CREATE TABLE price_quote (
    instrument_id   UUID PRIMARY KEY REFERENCES instrument (id) ON DELETE CASCADE,
    price           NUMERIC(28, 8) NOT NULL,
    previous_close  NUMERIC(28, 8),
    currency        VARCHAR(8) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT price_quote_price_positive CHECK (price > 0),
    CONSTRAINT price_quote_previous_positive CHECK (previous_close IS NULL OR previous_close > 0)
);

CREATE TABLE price_history (
    id              UUID PRIMARY KEY,
    instrument_id   UUID NOT NULL REFERENCES instrument (id) ON DELETE CASCADE,
    price_date      DATE NOT NULL,
    price           NUMERIC(28, 8) NOT NULL,
    currency        VARCHAR(8) NOT NULL,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT price_history_price_positive CHECK (price > 0),
    CONSTRAINT price_history_unique UNIQUE (instrument_id, price_date)
);

CREATE INDEX idx_price_history_lookup ON price_history (instrument_id, price_date);
