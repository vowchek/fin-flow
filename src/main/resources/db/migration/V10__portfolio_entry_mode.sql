ALTER TABLE stock_portfolio
    ADD COLUMN entry_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE crypto_portfolio
    ADD COLUMN entry_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE stock_portfolio
    ADD CONSTRAINT stock_portfolio_entry_mode CHECK (entry_mode IN ('LAZY', 'MANUAL', 'BROKER_REPORT', 'BROKER_API'));

ALTER TABLE crypto_portfolio
    ADD CONSTRAINT crypto_portfolio_entry_mode CHECK (entry_mode IN ('LAZY', 'MANUAL', 'BROKER_REPORT', 'BROKER_API'));
