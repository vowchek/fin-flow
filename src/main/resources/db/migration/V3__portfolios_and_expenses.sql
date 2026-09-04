DROP TABLE IF EXISTS money_entry;
DROP TABLE IF EXISTS time_entry;
DROP TABLE IF EXISTS project;

CREATE TABLE stock_portfolio (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_user (id),
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_stock_portfolio_owner_id ON stock_portfolio (owner_id);

CREATE TABLE stock_holding (
    id              UUID PRIMARY KEY,
    portfolio_id    UUID NOT NULL REFERENCES stock_portfolio (id) ON DELETE CASCADE,
    symbol          VARCHAR(32) NOT NULL,
    name            VARCHAR(120),
    quantity        NUMERIC(28, 8) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT stock_holding_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_stock_holding_portfolio_symbol UNIQUE (portfolio_id, symbol)
);

CREATE INDEX idx_stock_holding_portfolio_id ON stock_holding (portfolio_id);

CREATE TABLE crypto_portfolio (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_user (id),
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_crypto_portfolio_owner_id ON crypto_portfolio (owner_id);

CREATE TABLE crypto_holding (
    id              UUID PRIMARY KEY,
    portfolio_id    UUID NOT NULL REFERENCES crypto_portfolio (id) ON DELETE CASCADE,
    symbol          VARCHAR(32) NOT NULL,
    name            VARCHAR(120),
    quantity        NUMERIC(28, 8) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT crypto_holding_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uq_crypto_holding_portfolio_symbol UNIQUE (portfolio_id, symbol)
);

CREATE INDEX idx_crypto_holding_portfolio_id ON crypto_holding (portfolio_id);

CREATE TABLE expense_entry (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_user (id),
    category        VARCHAR(32) NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    occurred_month  DATE NOT NULL,
    note            VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT expense_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT expense_entry_month_day CHECK (EXTRACT(DAY FROM occurred_month) = 1)
);

CREATE INDEX idx_expense_entry_owner_month ON expense_entry (owner_id, occurred_month);
