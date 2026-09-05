-- Cash flag on holdings + transaction kinds for deposits / income

ALTER TABLE stock_holding
    ADD COLUMN cash BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE crypto_holding
    ADD COLUMN cash BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE stock_transaction
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'TRADE';

ALTER TABLE stock_transaction
    ADD COLUMN related_holding_id UUID REFERENCES stock_holding (id) ON DELETE SET NULL;

ALTER TABLE stock_transaction
    ADD CONSTRAINT stock_transaction_kind CHECK (kind IN ('TRADE', 'DEPOSIT', 'WITHDRAW', 'DIVIDEND', 'COUPON'));

ALTER TABLE crypto_transaction
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'TRADE';

ALTER TABLE crypto_transaction
    ADD COLUMN related_holding_id UUID REFERENCES crypto_holding (id) ON DELETE SET NULL;

ALTER TABLE crypto_transaction
    ADD CONSTRAINT crypto_transaction_kind CHECK (kind IN ('TRADE', 'DEPOSIT', 'WITHDRAW'));

CREATE INDEX idx_stock_transaction_related ON stock_transaction (related_holding_id);
CREATE INDEX idx_crypto_transaction_related ON crypto_transaction (related_holding_id);

INSERT INTO stock_holding (id, portfolio_id, symbol, name, quantity, cash, created_at, updated_at)
SELECT gen_random_uuid(), p.id, 'RUB', 'Рубли', 0, TRUE, NOW(), NOW()
FROM stock_portfolio p
WHERE NOT EXISTS (
    SELECT 1 FROM stock_holding h WHERE h.portfolio_id = p.id AND h.cash = TRUE
);

INSERT INTO crypto_holding (id, portfolio_id, symbol, name, quantity, cash, created_at, updated_at)
SELECT gen_random_uuid(), p.id, 'USD', 'Доллары', 0, TRUE, NOW(), NOW()
FROM crypto_portfolio p
WHERE NOT EXISTS (
    SELECT 1 FROM crypto_holding h WHERE h.portfolio_id = p.id AND h.cash = TRUE
);
