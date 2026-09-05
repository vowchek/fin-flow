-- Cashflow profile on MOEX instruments + portfolio tax for passive-income forecast.

ALTER TABLE stock_instrument
    ADD COLUMN asset_kind VARCHAR(16) NOT NULL DEFAULT 'EQUITY';

ALTER TABLE stock_instrument
    ADD CONSTRAINT stock_instrument_asset_kind CHECK (asset_kind IN ('EQUITY', 'BOND'));

ALTER TABLE stock_instrument
    ADD COLUMN annual_cashflow_per_unit NUMERIC(28, 8);

ALTER TABLE stock_instrument
    ADD COLUMN cashflow_growth_pct NUMERIC(12, 6);

ALTER TABLE stock_instrument
    ADD COLUMN cashflow_until_year INTEGER;

ALTER TABLE stock_portfolio
    ADD COLUMN tax_rate_percent NUMERIC(8, 4) NOT NULL DEFAULT 13;
