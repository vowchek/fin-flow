ALTER TABLE stock_portfolio
    ADD COLUMN invested_amount NUMERIC(28, 8);

ALTER TABLE crypto_portfolio
    ADD COLUMN invested_amount NUMERIC(28, 8);
